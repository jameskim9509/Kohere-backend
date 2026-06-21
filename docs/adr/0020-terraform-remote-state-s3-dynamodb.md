# ADR-0020. Terraform 원격 상태는 S3 + DynamoDB 잠금으로 둔다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0020 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0019](./0019-infrastructure-as-code-terraform.md), [infra/terraform](../../infra/terraform/README.md), [bootstrap](../../infra/terraform/bootstrap/README.md) |

## Status

Accepted

> [ADR-0019](./0019-infrastructure-as-code-terraform.md)(Terraform 채택)의 **세부 결정**이다 — Terraform 상태를 어디에 저장하고 동시 실행을 어떻게 막을지 정한다.

## Context

- Terraform은 **상태 파일(state)** 에 "코드 ↔ 실제 AWS 리소스" 매핑을 저장한다. 이 상태가 인프라의 정본이다.
- 상태를 **팀·CI가 공유**해야 하므로 로컬 파일은 부적합하다 — 공유 불가·유실 위험·머신 간 불일치.
- **여러 사람/CI가 동시에 `apply`** 하면 같은 상태를 동시에 써서 **손상되거나 리소스가 꼬일** 수 있다 → 동시 실행을 막는 **잠금(lock)** 이 필요하다.
- 상태에는 Terraform이 **생성한 비밀번호·키 등 민감정보**가 포함될 수 있어 **암호화·접근통제·버전관리**가 필요하다.
- 나머지 스택을 AWS 네이티브로 단일 운영하는 기조([ADR-0018](./0018-documentdb-for-mongodb-on-aws.md)/[ADR-0019](./0019-infrastructure-as-code-terraform.md))와 일관성을 유지하고 싶다.

## Decision

**원격 상태는 S3 버킷에 저장하고, DynamoDB 테이블로 상태 잠금을 건다**(Terraform 표준 원격 백엔드 조합).

- **S3(상태 저장)**: 버전 관리 + 서버 측 암호화(AES256) + 퍼블릭 액세스 전면 차단 + **TLS 강제**(`aws:SecureTransport=false` 거부). `key = prod/terraform.tfstate`로 환경별 분리.
- **DynamoDB(잠금)**: 해시키 `LockID`, `PAY_PER_REQUEST`. `plan`/`apply` 시 잠금 항목 1개를 잡고 끝나면 해제 → **동시 실행을 직렬화**(두 번째 실행은 lock 획득 실패로 차단). 평소엔 비어 있어 비용 사실상 0.
- **부트스트랩**: 백엔드(S3·DynamoDB)를 만드는 `bootstrap` 구성은 **로컬 state**로 1회 `apply`하고, 출력값을 `environments/prod/backend.tf`에 채운 뒤 `terraform init -reconfigure`로 연결한다(닭-달걀 해소). 자세한 절차는 [bootstrap README](../../infra/terraform/bootstrap/README.md).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **S3 + DynamoDB (채택)** | AWS 네이티브·널리 검증된 표준·암호화/버전관리/잠금 완비 | 잠금용 테이블 1개 운영(소액)·부트스트랩 1회 필요 | — |
| **S3 + `use_lockfile` (Terraform 1.11+)** | DynamoDB 불필요(잠금도 S3 객체로) | Terraform **1.11+ 고정** 필요 | 현재 `>= 1.6`으로 폭넓은 호환 유지 — **TF 1.11+ 표준화 시 전환 후보**(아래 트리거) |
| **S3 only(잠금 없음)** | 가장 단순 | 동시 `apply` 시 상태 손상 위험 | 팀/CI 공유 환경에서 위험 |
| **Terraform Cloud / HCP** | 매니지드 상태·잠금·정책·UI | 외부 SaaS·계정·비용·AWS 단일 운영에서 이탈 | 작은 팀 MVP엔 과함, AWS 네이티브 일관성 선호([ADR-0018](./0018-documentdb-for-mongodb-on-aws.md) 기조) |
| **로컬 state** | 즉시 | 공유 불가·유실·동시성 없음·시크릿 노출 | 협업·CI 불가 |

## Consequences

- **긍정**
  - 상태 **공유·잠금·암호화·버전관리**를 모두 확보.
  - 전부 **AWS 네이티브**라 [ADR-0018](./0018-documentdb-for-mongodb-on-aws.md)/[ADR-0019](./0019-infrastructure-as-code-terraform.md) 기조와 일치.
  - 비용 사실상 0(DynamoDB 온디맨드, `LockID` 1항목 + S3 소량).
- **부정/트레이드오프**
  - **부트스트랩 1회 수동 단계**가 필요(백엔드를 만드는 구성 자체는 로컬 state).
  - 상태에 시크릿이 포함될 수 있음 → S3 암호화·퍼블릭 차단·TLS·IAM 접근통제로 보호.
  - 잠금용 **DynamoDB 테이블 1개**가 추가된다(앱 DB 아님, Terraform 실행 시점에만 접근).
- **후속 작업**
  - `bootstrap` apply → `backend.tf` 채우고 `init -reconfigure`([README](../../infra/terraform/README.md)).
  - 팀이 Terraform **1.11+로 표준화하면** `use_lockfile = true`로 전환하고 DynamoDB 테이블을 제거하는 안을 검토.

## Validation

- `bootstrap` apply로 S3 버킷·DynamoDB 테이블 생성, `prod`에서 `init -reconfigure`로 원격 백엔드 연결 확인.
- 동시 `apply` 시 두 번째 실행이 **lock 획득 실패로 차단**되는지 확인.
- **재검토 트리거**: Terraform 1.11+ 고정 시 `use_lockfile`로 전환(DynamoDB 제거), 또는 환경이 늘면 상태 `key` 분리 정책 정비.
