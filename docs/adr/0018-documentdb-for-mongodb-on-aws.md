# ADR-0018. MongoDB 호환 저장소는 Amazon DocumentDB로 운영한다(Atlas 대비)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0018 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0005](./0005-polyglot-persistence.md), [ADR-0006](./0006-refresh-token-store-redis.md), [system-overview §1-3-2](../architecture/system-overview.md#1-3-2-클라우드-배포-아키텍처-m7-이전배포-aws), [listings spec](../api/specs/03-listings-favorites.md), [infra/terraform](../../infra/terraform/README.md) |

## Status

Accepted

> [ADR-0005](./0005-polyglot-persistence.md)가 `listing`(+찜·최근본)·`diagnosis`를 **MongoDB**로 두기로 했으나 **매니지드 제공자(Atlas vs DocumentDB)는 열어뒀다**("Atlas 또는 DocumentDB"). 본 ADR은 M7 AWS 이전·배포(IaC)를 위해 그 제공자를 확정하는 [ADR-0005](./0005-polyglot-persistence.md)의 후속이다.

## Context

- [ADR-0005](./0005-polyglot-persistence.md): `listing`(+`favorite`·`recent-listing`)·`diagnosis`는 데이터 특성(지오·문서·가변 스키마)상 MongoDB로 둔다. 단, **클라우드 매니지드 제공자는 미결**로 남겼다([system-overview §1-3-2](../architecture/system-overview.md) 표에 "Atlas 또는 DocumentDB").
- M7(7/8–7/10) AWS 이전·배포를 **Terraform(IaC)** 으로 구성해야 한다. 매니지드 MongoDB 제공자를 하나 확정해야 인프라 모듈(`modules/documentdb` 등)을 작성·검증할 수 있다.
- 나머지 매니지드 스택(RDS·ElastiCache·ALB·ECS Fargate·S3+CloudFront·Secrets Manager·ECR)은 전부 **AWS 네이티브**로 단일 계정·단일 VPC·단일 Terraform provider에서 운영된다([infra/terraform](../../infra/terraform/README.md)).
- 팀 규모가 작고 MVP 운영 기간이 짧아, **별도 SaaS 벤더의 계정·결제·접근통제 거버넌스 부담**을 최소화해야 한다.
- `listing` 지도검색(F-02, **보호 핵심**)은 `2dsphere` + `$geoWithin`(bbox)·`$near`(거리)·`$geoNear`(집계)에 의존한다([ADR-0005](./0005-polyglot-persistence.md), [listings spec](../api/specs/03-listings-favorites.md)).
- 후보: ① **MongoDB Atlas**(SaaS), ② **Amazon DocumentDB**(AWS 관리형, Mongo 호환), ③ **EC2 자체 운영 MongoDB**.

## Decision

**MongoDB 호환 워크로드(`listing`·`diagnosis`)는 Amazon DocumentDB(5.0)로 운영한다.** Atlas는 채택하지 않는다(단, 아래 *지오 호환성 트리거* 시 전환).

선택 기준(우선순위 순):

1. **AWS 네이티브 일관성** — 단일 클라우드 계정·VPC 프라이빗 서브넷·**단일 Terraform provider**. IAM·보안그룹·Secrets Manager·KMS·자동 백업·CloudWatch를 RDS·ElastiCache와 **동일 패턴**으로 관리한다. (Atlas는 별도 SaaS 계정 + `mongodbatlas` provider + PrivateLink/VPC peering가 필요하다.)
2. **운영 단순성·거버넌스** — 외부 벤더 계정·결제·접근통제 분리가 불필요. 작은 팀·짧은 MVP에 유리.
3. **보안** — 데이터 계층을 **VPC 내부 격리 서브넷**에 두는 폴리글랏 원칙([ADR-0005](./0005-polyglot-persistence.md))과 자연스럽게 맞는다.

세부:

- DocumentDB 5.0, **프라이빗 데이터 서브넷**(인터넷 격리), 저장 암호화(KMS)·자동 백업·삭제보호, **app 보안그룹에서만 27017 인바운드**.
- 연결 자격증명·URI는 **Secrets Manager**에 저장하고 ECS 태스크에 주입(`SPRING_DATA_MONGODB_URI`).
- **TLS 기본 enabled** — Mongo 드라이버 배선 시 앱 이미지에 Amazon DocumentDB CA 번들(`global-bundle.pem`) 포함이 필요하다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. Amazon DocumentDB (채택)** | AWS 네이티브(단일 provider·VPC 내부), RDS류와 동일 운영(IAM·KMS·백업·CloudWatch), 관리형 HA | MongoDB **100% 호환 아님**(지오·일부 집계/연산자 갭), 인스턴스 기반(최소 `db.t3.medium`), 최신 Mongo 기능 지연 | — |
| **B. MongoDB Atlas** | 100% 호환·서버리스/오토스케일·멀티클라우드, 풍부한 지오 | 별도 SaaS 계정·`mongodbatlas` provider·PrivateLink/peering, 벤더 거버넌스·결제 분리 | MVP 운영 단순성·AWS 일관성을 우선. **단 지오 호환성 갭 발견 시 1순위 전환 후보** |
| **C. EC2 자체 운영 MongoDB** | 완전한 호환·제어 | 패치·백업·HA·모니터링을 직접 운영 | 작은 팀·짧은 기한에 운영 부담 과다 |

## Consequences

- **긍정**
  - 전 스택을 **단일 AWS provider/계정**으로 IaC 일관 관리([infra/terraform](../../infra/terraform/README.md)).
  - 보안·백업·암호화(KMS)·모니터링이 RDS·ElastiCache와 **동일 패턴** → 학습·운영 비용 절감.
  - 외부 SaaS 벤더 의존·결제·계정 거버넌스 부담 없음.
- **부정/트레이드오프**
  - **지오공간 호환성 리스크(최우선 관리 대상)** — `listing` 지도검색이 `2dsphere`·`$geoNear`/`$geoWithin`에 의존하는데 DocumentDB의 호환 범위를 **검증**해야 한다. 갭 발견 시 대안 B(Atlas)로 전환한다(*트리거*).
  - **로컬↔운영 엔진 불일치** — 로컬 개발은 `mongo` 컨테이너(docker-compose), 운영은 DocumentDB라 호환 갭이 로컬에서 드러나지 않을 수 있다 → 실 DocumentDB 스모크 검증으로 보완.
  - 인스턴스 기반(상시 가동·최소 `db.t3.medium`)이라 트래픽 적은 MVP엔 다소 과할 수 있다.
  - 최신 MongoDB 기능/일부 집계 연산자 미지원 가능.
  - TLS 사용 시 앱 이미지에 DocumentDB CA 번들 포함 필요.
- **후속 작업**
  - `infra/terraform`의 `modules/documentdb` 프로비저닝(**완료** — 프라이빗 서브넷·KMS·백업·삭제보호·Secrets Manager URI).
  - Mongo 드라이버 배선 시 `SPRING_DATA_MONGODB_URI`(`tls=true`) 적용 + CA 번들 포함.
  - `listing` 지도검색 지오 쿼리의 DocumentDB 적합성 검증(아래 Validation).
  - [system-overview](../architecture/system-overview.md)·[project-brief](../project/project-brief.md) 표기 정합(**완료**).

## Validation

- **지오 적합성(핵심)**: `listing` 지도/거리/반경 질의(`2dsphere`·bbox·`$geoNear`)가 DocumentDB에서 의도대로 동작하는지 검증한다 — (a) AWS DocumentDB 지오공간 지원 매트릭스 대조 + (b) **실 DocumentDB 인스턴스에서 스모크 테스트**(로컬 `mongo`로는 호환 갭이 안 드러나므로). 실패 시 **Atlas(대안 B)로 전환**한다.
- **운영 검증**: `terraform apply`로 프로비저닝 후 ECS 태스크에서 실연결·헬스 확인.
- **재검토 시점**: 지오/집계 호환성 갭, 상시가동 비용 부담, 또는 멀티클라우드 요구가 생기면 대안 B(Atlas)를 재검토한다. (제공자 전환 비용은 모듈 경계상 `listing`·`diagnosis` 영속 어댑터에 국한된다.)
