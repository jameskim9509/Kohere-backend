# ADR-0019. AWS 인프라를 Terraform(IaC)으로 관리한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0019 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0018](./0018-documentdb-for-mongodb-on-aws.md), [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md), [system-overview §1-3-2](../architecture/system-overview.md#1-3-2-클라우드-배포-아키텍처-m7-이전배포-aws), [infra/terraform](../../infra/terraform/README.md) |

## Status

Accepted

> [system-overview §1-3-2](../architecture/system-overview.md)가 M7 AWS 배포 토폴로지(ECS Fargate·RDS·DocumentDB·ElastiCache·ALB·S3+CloudFront·Secrets Manager·ECR)를 정했다. 본 ADR은 그 인프라를 **무엇으로 정의·프로비저닝할지**(IaC 도구와 구조)를 결정한다. 매니지드 MongoDB 제공자 선택은 [ADR-0018](./0018-documentdb-for-mongodb-on-aws.md), 상태 백엔드는 [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md)에서 다룬다.

## Context

- M7(7/8–7/10)에 AWS로 이전·배포해야 하며, 토폴로지가 **다수의 매니지드 서비스**로 구성된다([system-overview §1-3-2](../architecture/system-overview.md)).
- **콘솔 수동 구성은 재현 불가·드리프트·리뷰 불가**다. 인프라를 코드로 두어 버전관리·PR 리뷰·재현·CI 검증 대상으로 만들어야 한다.
- 팀은 이미 **GitHub PR 기반 협업 + CI**로 일한다([collaboration-convention](../convention/collaboration-convention.md)). 인프라도 같은 흐름(코드 리뷰·CI)에 태우는 것이 자연스럽다.
- 멀티클라우드 계획은 없고 **단일 계정·단일 리전(ap-northeast-2)·prod 단일 환경**(현재). 단, dev/stage 복제 여지는 남겨야 한다.
- 후보 도구: **Terraform**, AWS CloudFormation, AWS CDK, Pulumi, 콘솔 수동.

## Decision

**AWS 인프라를 Terraform(HCL)으로 정의·프로비저닝한다.**

- **구조 = 재사용 모듈(`modules/*`) + 환경 루트(`environments/prod`).** 모듈은 책임 단위로 나눈다(`network`·`security`·`iam`·`alb`·`ecs`·`rds`·`documentdb`·`elasticache`·`ecr`·`secrets`·`s3-cloudfront`·`acm`·`monitoring`). 환경 루트가 모듈을 배선하고 변수로 규모·도메인·시크릿을 주입한다. → dev/stage는 같은 모듈을 다른 환경 루트에서 재사용해 추가한다.
- **버전 고정으로 재현성 확보.** Terraform `>= 1.6`, AWS provider **`~> 6.0`**, `.terraform.lock.hcl`을 커밋한다.
- **포맷·검증을 게이트로.** 커밋/CI 전 `terraform fmt -check`·`terraform validate`를 통과시킨다.
- **상태 백엔드는 [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md)에서 별도 결정**(S3 + DynamoDB).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **Terraform (채택)** | 거대 생태계·모듈/HCL 성숙·상태 기반 `plan`/diff로 변경 사전 확인·멀티클라우드 여지·팀 친숙 | 상태 파일 운영 필요([ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md))·HCL은 복잡 로직 표현에 한계 | — |
| **AWS CloudFormation** | AWS 네이티브·상태를 서비스가 관리(별도 백엔드 불필요)·드리프트 감지 | AWS 종속·YAML 장황·모듈성(중첩 스택) 약함·생태계 작음 | 모듈 재사용성·`plan` diff·팀 친숙도에서 Terraform 우위 |
| **AWS CDK** | 범용 언어(TS 등)·강력한 추상화 | 내부적으로 CloudFormation 합성(동일 한계)·러닝커브·합성 디버깅 | 팀이 선언형 HCL 선호, 합성 계층 불필요 |
| **Pulumi** | 범용 언어·강력 | 상태 기본 SaaS·생태계가 Terraform보다 작음·팀 미경험 | Terraform 대비 도입 이점 부족 |
| **콘솔 수동** | 즉시·러닝커브 0 | 재현 불가·드리프트·리뷰 불가·문서화 안 됨 | 협업·운영 불가, 즉시 탈락 |

## Consequences

- **긍정**
  - 인프라가 코드로 **버전관리·PR 리뷰·재현 가능**, `plan`으로 변경을 사전 확인한다.
  - **모듈 재사용**으로 dev/stage 환경 복제가 쉽다.
  - lock 파일로 provider 버전을 고정해 "내 머신에선 됐는데" 류 표류를 막는다.
- **부정/트레이드오프**
  - 상태 파일을 안전하게 운영해야 한다(→ [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md)).
  - HCL로 복잡한 절차적 로직을 표현하기 어렵다.
  - provider 메이저 업그레이드(6.x→7.x) 시 호환 점검이 필요하다(lock + `~> 6.0`으로 통제).
  - 생성된 시크릿이 상태에 포함될 수 있어 상태 보호가 필수다([ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md)).
- **후속 작업**
  - `infra/terraform` 모듈·환경 작성(**완료**).
  - CI에 `terraform fmt -check`·`validate`(필요 시 `plan`) 단계 추가.
  - dev/stage가 필요해지면 모듈 재사용으로 환경 루트를 추가.

## Validation

- `terraform fmt -check` / `terraform validate`가 green (**완료**: Terraform 1.9.8 / AWS provider 6.x).
- `terraform plan`/`apply`가 [system-overview §1-3-2](../architecture/system-overview.md) 토폴로지를 재현하는지 M7 배포로 검증.
- **드리프트 관측**: 정기 `terraform plan`으로 콘솔 수동 변경을 감지.
- **재검토 시점**: 환경/복잡도가 늘어 모듈 경계나 도구(예: CDK/Terragrunt)를 재고할 필요가 생기면 재검토.
