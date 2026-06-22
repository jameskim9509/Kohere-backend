# 원격 상태(state) 백엔드 — bootstrap을 먼저 apply한 뒤 아래 bucket 값을 채우고
#   terraform init -reconfigure
# 를 실행한다. (bucket 값은 bootstrap의 출력값.)
# 잠금은 S3 native lockfile(use_lockfile, TF 1.10+) — DynamoDB 불요.
terraform {
  backend "s3" {
    bucket       = "REPLACE_WITH_BOOTSTRAP_STATE_BUCKET"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
