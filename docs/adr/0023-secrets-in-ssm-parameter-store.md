# ADR-0023. 시크릿은 SSM Parameter Store SecureString에 둔다 (Secrets Manager 미사용)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0023 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0019](./0019-infrastructure-as-code-terraform.md), [ADR-0021](./0021-cost-optimization-profile.md), [infra/terraform](../../infra/terraform/README.md) |

## Status

Accepted

> 앱·DB 시크릿(JWT 서명키·pepper·OIDC audience·SMTP 자격증명·DB 비밀번호·Mongo URI)을 **어디에 저장하고 어떻게 주입할지** 정한다. 비용 최소화 기조([ADR-0021](./0021-cost-optimization-profile.md))에 맞춰 **Secrets Manager 대신 SSM Parameter Store SecureString**(무료)을 쓴다. prod·dev 공통.

## Context

- 시크릿은 **암호화 저장 + 런타임 주입**(ECS 태스크, dev EC2 컨테이너)이 필요하고, Terraform state에 평문이 들어가지 않게 관리해야 한다.
- AWS의 두 선택지:
  - **Secrets Manager**: 시크릿당 **$0.40/월** + API 호출 과금. 자동 회전(rotation)·교차계정 등 기능 풍부.
  - **SSM Parameter Store SecureString(standard tier)**: **무료**(API 호출도 standard는 무과금). 회전 기능은 없음, 값 크기 4KB 제한.
- Kohere는 시크릿이 5~10개 수준이고 **자동 회전 요구가 아직 없다** → Secrets Manager의 핵심 차별 기능을 쓰지 않으면서 **월 고정비만 발생**한다.
- ECS 태스크 정의의 `secrets`(valueFrom)는 **Secrets Manager·SSM Parameter Store 둘 다** 참조할 수 있다. 단 **차이가 있다**: Secrets Manager는 `arn:...:secret:name:json-key::`로 **JSON 키 추출**을 지원하지만, **SSM Parameter Store는 파라미터 값 전체만** 주입한다(JSON 키 추출 불가).

## Decision

**모든 시크릿을 SSM Parameter Store SecureString 파라미터로 저장하고, Secrets Manager를 쓰지 않는다.**

- **키마다 별도 파라미터**: SSM은 JSON 키 추출이 안 되므로, 앱이 읽는 env 단위로 파라미터를 1:1로 만든다 — `/<name_prefix>/<ENV_NAME>` (예: `/kohere-prod/JWT_SECRET`, `/kohere-prod/SPRING_DATASOURCE_PASSWORD`, `/kohere-prod/SPRING_DATA_MONGODB_URI`).
- **자동 생성 + 외부 발급**: `JWT_SECRET`·`REFRESH_PEPPER`·`EMAIL_PEPPER`는 Terraform `random_password`로 생성. `GOOGLE_CLIENT_ID`·SMTP 자격증명 등은 변수로 받는다. RDS 비밀번호·DocumentDB URI도 각 모듈이 SecureString 파라미터로 저장한다.
- **빈 값 처리**: SSM은 빈 SecureString을 거부하므로, 외부 발급값이 비면 `REPLACE_ME` placeholder로 두고 운영 전 tfvars로 채운다.
- **prod 주입(ECS)**: 태스크 정의 `container_secrets`의 `valueFrom = <파라미터 ARN>`(값 전체가 곧 시크릿). 태스크 **실행 역할**에 `ssm:GetParameters` + `kms:Decrypt`(`alias/aws/ssm` 키)를 최소권한으로 부여한다.
- **dev 주입(EC2)**: 인스턴스 프로파일에 `ssm:GetParameter`(해당 접두사) + `kms:Decrypt`를 부여하고, user_data가 부팅 시 `get-parameter --with-decryption`으로 받아 `.env`(0600)에 써서 compose에 주입한다.
- **키**: 기본 `alias/aws/ssm`(AWS 관리형, **무료**)로 SecureString을 암호화한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **SSM Parameter Store SecureString (채택)** | **무료**, ECS/EC2 주입 지원, KMS 암호화 | JSON 키 추출 불가(키마다 파라미터), 자동 회전 없음, 4KB 한도 | — |
| **Secrets Manager** | JSON 단일 시크릿·키 추출·자동 회전 | 시크릿당 $0.40/월 + API 과금 | 회전 미사용인데 고정비 발생 — 비용 기조([ADR-0021](./0021-cost-optimization-profile.md))에 반함 |
| **SSM Advanced tier** | 8KB·정책·더 많은 파라미터 | 파라미터당 과금 | standard로 충분 |

## Consequences

- **긍정**
  - 시크릿 저장 **비용 0**(standard SecureString + aws/ssm 키). Secrets Manager 고정비 제거.
  - prod·dev **동일 방식**(Parameter Store)이라 일관성↑. ECS는 `valueFrom`, dev는 `.env` 주입.
  - state 보호는 동일(원격 state S3 암호화, [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md)).
- **부정/트레이드오프**
  - **JSON 키 추출 불가** → 시크릿 묶음을 키마다 파라미터로 쪼개야 한다(파라미터 수↑, 관리 포인트↑).
  - **자동 회전 없음** — 회전이 필요해지면 Lambda 등으로 직접 구현하거나 Secrets Manager로 전환해야 한다.
  - 값 4KB 한도(standard) — 현재 시크릿엔 충분.
- **후속 작업**
  - 회전 정책이 필요해지면 재검토(Secrets Manager 또는 커스텀 회전).
  - 외부 발급값(`REPLACE_ME`)을 운영 전 실제 값으로 채운다.

## Validation

- apply 후 `/<name_prefix>/*` SecureString 파라미터가 생성되고, ECS 태스크/dev 앱이 정상 기동(시크릿 주입 성공)하는지 확인.
- 태스크 실행 역할·dev 인스턴스 프로파일이 **지정 파라미터만** 읽고 `kms:Decrypt`가 동작하는지 확인.
- state·로그·`docker inspect`에 평문 시크릿이 노출되지 않는지 확인.
