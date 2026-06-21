# Terraform 원격 상태 백엔드 부트스트랩

`environments/prod` 가 사용할 원격 상태 저장소(S3) + 잠금 테이블(DynamoDB)을 만든다.
**`environments/prod` 보다 먼저 1회** 실행한다. 이 구성 자체는 로컬 state(`terraform.tfstate`)를 쓴다(gitignore됨).

## 실행

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply
```

출력된 값을 `environments/prod/backend.tf` 에 채운다:

```hcl
terraform {
  backend "s3" {
    bucket         = "<state_bucket_name 출력값>"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "<lock_table_name 출력값>"
    encrypt        = true
  }
}
```

그 다음 `environments/prod` 에서 `terraform init -reconfigure` 로 원격 백엔드를 연결한다.

> 버킷 이름은 전역 유일해야 하므로 기본값은 `<project>-tfstate-<account_id>` 다. 필요하면 `state_bucket_name` 으로 덮어쓴다.
