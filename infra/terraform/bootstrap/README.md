# Terraform 원격 상태 백엔드 부트스트랩

`environments/{prod,dev}` 가 공유할 원격 상태 저장소(S3 버킷)를 만든다. 잠금은 **S3 native lockfile**(`use_lockfile`, TF 1.10+)이라 별도 DynamoDB 테이블이 필요 없다.
**`environments/{prod,dev}` 보다 먼저 1회** 실행한다.

> **닭-달걀**: 이 구성이 만드는 S3 버킷이 아직 없는 최초 1회는 backend(S3)를 켤 수 없다(첫 `init` 실패).
> 그래서 **로컬 state로 먼저 apply** 한 뒤, 그 state를 방금 만든 S3로 **이전(migrate)** 한다.

## 1) 최초 apply — 로컬 state로 버킷 생성

[backend.tf](backend.tf) 의 `backend "s3"` 블록은 주석 처리된 상태여야 한다(초기값).

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply
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
