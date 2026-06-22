# Terraform 부트스트랩 — 원격 상태 백엔드 + 공유 자원(OIDC·ECR)

`environments/{prod,dev}` 가 공유할 세 가지를 만든다(이들보다 **먼저 1회** 실행):

- **원격 상태 저장소(S3 버킷)** — 잠금은 **S3 native lockfile**(`use_lockfile`, TF 1.10+)이라 별도 DynamoDB 테이블이 필요 없다.
- **GitHub Actions OIDC provider** — 계정당(URL당) 1개인 싱글톤. bootstrap이 단일 소유하고 `environments/{prod,dev}` 는 `data` 로 조회만 한다(생성 충돌 방지). 상세는 [§4](#4-github-actions-oidc-provider).
- **앱 이미지 ECR 리포지토리** — dev·prod가 같은 이미지를 쓰는 공유 리포. bootstrap이 단일 생성하고 환경들은 이름(`data`)으로 조회만 한다. 상세는 [§5](#5-앱-이미지-ecr-리포지토리).

> **닭-달걀**: 이 구성이 만드는 S3 버킷이 아직 없는 최초 1회는 backend(S3)를 켤 수 없다(첫 `init` 실패).
> 그래서 **로컬 state로 먼저 apply** 한 뒤, 그 state를 방금 만든 S3로 **이전(migrate)** 한다.

## 1) 최초 apply — 로컬 state로 버킷·OIDC·ECR 생성

[backend.tf](backend.tf) 의 `backend "s3"` 블록은 주석 처리된 상태여야 한다(초기값).

`state_bucket_name` 만 default 가 없는 필수 변수다(나머지는 default 보유). 비우면 아래 `apply` 가 대화형으로 물어보고, 비대화형으로 돌리려면 [terraform.tfvars.example](terraform.tfvars.example) 을 복사해 채운다.

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply   # state_bucket_name 미지정 시 프롬프트로 입력 (예: kohere-tfstate-<account_id>)
```

## 2) bootstrap state를 S3로 이전 (migrate-state)

[backend.tf](backend.tf) 의 블록 주석을 풀고 `bucket` 을 위 apply 출력값으로 채운 뒤:

```bash
terraform init -migrate-state   # 로컬 terraform.tfstate → S3(bootstrap/terraform.tfstate)
```

이제 로컬 `terraform.tfstate` 는 비워지고 state는 S3에 안전하게 보관된다. (prod·dev와 같은 버킷, `key` 만 다름.)

## 3) prod·dev 백엔드 연결

출력값(`state_bucket_name`)을 `environments/prod/backend.tf` 와 `environments/dev/backend.tf` 의 bucket 에 채운다(`key` 로 환경 분리 — `prod/terraform.tfstate`, `dev/terraform.tfstate`):

```hcl
terraform {
  backend "s3" {
    bucket       = "<state_bucket_name 출력값>"
    key          = "prod/terraform.tfstate" # dev 는 "dev/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
```

그 다음 각 환경에서 `terraform init -reconfigure` 로 원격 백엔드를 연결한다.

> 버킷 이름은 전역 유일해야 하므로 `state_bucket_name` 으로 **직접 지정**한다(필수 — 자동생성 없음). 예: `kohere-tfstate-<account_id>`.

## 4) GitHub Actions OIDC provider

CI/CD(GitHub Actions)가 **장기 액세스 키 없이** AWS 역할을 assume하려면 OIDC provider(`token.actions.githubusercontent.com`)가 계정에 있어야 한다. 이 provider는 **계정당(정확히는 URL당) 1개**만 존재할 수 있는 싱글톤이라, 여러 환경이 각자 만들면 생성 충돌이 난다. 그래서 **bootstrap이 단독으로 생성·소유**하고([main.tf](main.tf) 의 `aws_iam_openid_connect_provider.github`), `environments/{prod,dev}` 의 배포 역할(`cicd.tf`)은 이를 `data "aws_iam_openid_connect_provider"` 로 **조회만** 한다.

따라서 **이 bootstrap을 먼저 apply** 해야 prod·dev의 provider lookup이 성공한다 — 안 돼 있으면 환경 apply가 provider를 못 찾아 실패한다. provider는 위 1)의 `terraform apply` 에서 S3 버킷과 함께 만들어진다(별도 명령 불필요).

provider ARN은 `github_oidc_provider_arn` 으로 출력된다(참고용 — 환경들은 이 값을 직접 받지 않고 각자 `data` 로 다시 조회한다):

```bash
terraform output github_oidc_provider_arn
```

## 5) 앱 이미지 ECR 리포지토리

dev·prod는 **같은 앱 이미지**(같은 빌드)를 쓴다 → ECR 리포지토리(`kohere-backend`)도 **공유 자원**이다. 어느 한 환경이 만들면 다른 환경과 생성 충돌이 나므로, **bootstrap이 단일 생성**하고([main.tf](main.tf) 의 `aws_ecr_repository.app` + 보관 lifecycle) 환경들은 이름으로만 참조한다:

- **dev** — `var.ecr_repository`(기본 `kohere-backend`)로 ARN을 조립해 CI push 권한·호스트 pull 권한·`app_image` URI에 쓴다.
- **prod** — `data "aws_ecr_repository"` 로 조회해 ECS 태스크 이미지·배포 역할 IAM에 쓴다.

따라서 **이 bootstrap을 먼저 apply** 해야 dev/prod가 ECR을 참조할 수 있다(없으면 prod의 data 조회 실패, dev 호스트의 이미지 pull 실패). provider처럼 위 1)의 `terraform apply` 에서 함께 만들어진다. 리포명·보관 개수는 `ecr_repository`·`ecr_image_retention_count` 변수로 바꾼다. 출력:

```bash
terraform output ecr_repository_url   # CI push·compose pull 대상
```
