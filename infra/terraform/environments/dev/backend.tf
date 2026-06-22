# 원격 상태(state) 백엔드 — prod와 같은 버킷, key만 분리(dev/terraform.tfstate).
# bootstrap을 먼저 apply한 뒤 bucket 값을 채우고 terraform init -reconfigure.
# 잠금은 S3 native lockfile(use_lockfile, TF 1.10+) — DynamoDB 불요.
terraform {
  backend "s3" {
    bucket       = "kohere-tfstate-471597061265"
    key          = "dev/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
