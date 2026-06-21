# ADR-0020. Terraform 원격 상태는 S3 + native lockfile로 둔다 (DynamoDB 불요)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0020 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0019](./0019-infrastructure-as-code-terraform.md), [infra/terraform](../../infra/terraform/README.md), [bootstrap](../../infra/terraform/bootstrap/README.md) |

## Status

Accepted

> [ADR-0019](./0019-infrastructure-as-code-terraform.md)(Terraform 채택)의 **세부 결정**이다 — Terraform 상태를 어디에 저장하고 동시 실행을 어떻게 막을지 정한다. 잠금은 별도 DynamoDB 없이 **S3 native lockfile**(`use_lockfile`)로 한다.

## Context

- Terraform은 **상태 파일(state)** 에 "코드 ↔ 실제 AWS 리소스" 매핑을 저장한다. 이 상태가 인프라의 정본이다.
- 상태를 **팀·CI가 공유**해야 하므로 로컬 파일은 부적합하다 — 공유 불가·유실 위험·머신 간 불일치.
- **여러 사람/CI가 동시에 `apply`** 하면 같은 상태를 동시에 써서 **손상되거나 리소스가 꼬일** 수 있다 → 동시 실행을 막는 **잠금(lock)** 이 필요하다.
- 상태에는 Terraform이 **생성한 비밀번호·키 등 민감정보**가 포함될 수 있어 **암호화·접근통제·버전관리**가 필요하다.
- Terraform **1.10부터 S3 백엔드가 native lockfile**(`use_lockfile = true`)을 지원한다 — S3의 조건부 쓰기(If-None-Match)로 `<key>.tflock` 객체를 만들어 동시 실행을 직렬화한다. 별도 DynamoDB 테이블이 필요 없고(운영 리소스 1개 감소), DynamoDB 기반 잠금은 1.11에서 deprecated 됐다.

## Decision

**원격 상태는 S3 버킷에 저장하고, 잠금은 S3 native lockfile(`use_lockfile = true`)로 건다 — DynamoDB 테이블을 쓰지 않는다.**

- **S3(상태 저장)**: 버전 관리 + 서버 측 암호화(AES256) + 퍼블릭 액세스 전면 차단 + **TLS 강제**(`aws:SecureTransport=false` 거부). `key`로 환경 분리(`prod/terraform.tfstate`, `dev/terraform.tfstate`).
- **잠금(S3 lockfile)**: backend `s3` 블록에 `use_lockfile = true`. `plan`/`apply` 시 `<key>.tflock` 객체를 조건부로 생성해 잠그고 끝나면 삭제 → **동시 실행을 직렬화**(두 번째 실행은 lockfile 존재로 차단). 추가 리소스·비용 없음(S3 객체 1개, 실행 중에만 존재).
- **요구 버전**: `required_version >= 1.10.0`(use_lockfile 지원 하한).
- **부트스트랩**: 백엔드(S3 버킷)를 만드는 `bootstrap` 구성은 버킷이 아직 없으므로 **최초 1회만 로컬 state**로 `apply`한다(닭-달걀 해소). 이후 **자기 state도 같은 버킷으로 이전**한다 — `backend "s3"`(`key = bootstrap/terraform.tfstate`, `use_lockfile = true`)를 켜고 `terraform init -migrate-state` 실행. 그다음 출력값(버킷 이름)을 `environments/{prod,dev}/backend.tf`에 채워 `init -reconfigure`로 연결한다. 자세한 절차는 [bootstrap README](../../infra/terraform/bootstrap/README.md).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **S3 + native lockfile (채택)** | DynamoDB 불필요(운영 리소스↓·비용 0)·AWS 네이티브·암호화/버전관리/잠금 완비 | Terraform **1.10+** 필요 | — |
| **S3 + DynamoDB 잠금** | 1.10 이전부터 동작한 오랜 표준 | 잠금용 **테이블 1개 추가 운영**, 1.11에서 deprecated | native lockfile이 더 단순·리소스 0이라 불필요 |
| **S3 only(잠금 없음)** | 가장 단순 | 동시 `apply` 시 상태 손상 위험 | 팀/CI 공유 환경에서 위험 |
| **Terraform Cloud / HCP** | 매니지드 상태·잠금·정책·UI | 외부 SaaS·계정·비용·AWS 단일 운영에서 이탈 | 작은 팀 MVP엔 과함, AWS 네이티브 일관성 선호 |
| **로컬 state** | 즉시 | 공유 불가·유실·동시성 없음·시크릿 노출 | 협업·CI 불가 |

## Consequences

- **긍정**
  - 상태 **공유·잠금·암호화·버전관리**를 모두 확보하면서 **잠금 전용 리소스(DynamoDB)가 0** — 운영·비용·관리 표면이 줄었다.
  - 전부 **S3 한 서비스**로 처리되어 AWS 네이티브 일관성 유지([ADR-0019](./0019-infrastructure-as-code-terraform.md)).
  - 비용 사실상 0(lockfile은 실행 중에만 존재하는 S3 객체 1개).
- **부정/트레이드오프**
  - **Terraform 1.10+ 고정** — 팀·CI 모두 1.10 이상이어야 한다(`required_version`으로 강제).
  - **부트스트랩 1회 수동 단계**가 필요(백엔드를 만드는 구성은 최초 1회만 로컬 state → 이후 `init -migrate-state`로 같은 버킷의 `bootstrap/` key로 이전).
  - 상태에 시크릿이 포함될 수 있음 → S3 암호화·퍼블릭 차단·TLS·IAM 접근통제로 보호.
- **후속 작업**
  - `bootstrap` apply → `prod`·`dev` `backend.tf`의 bucket 채우고 `init -reconfigure`([README](../../infra/terraform/README.md)).

## Validation

- `bootstrap` apply로 S3 버킷 생성, `prod`·`dev`에서 `init -reconfigure`로 원격 백엔드 연결 확인.
- 동시 `apply` 시 두 번째 실행이 **lockfile 존재로 차단**되는지 확인(`<key>.tflock`이 실행 중 생성·종료 시 삭제).
- **재검토 트리거**: 환경이 더 늘면 상태 `key` 분리 정책 정비, 또는 교차계정 운영이 필요해지면 백엔드 전략 재검토.
