# 원격 상태(state) 백엔드 — bootstrap을 먼저 apply한 뒤 아래 bucket·dynamodb_table 값을 채우고
#   terraform init -reconfigure
# 를 실행한다. (bucket/table 값은 bootstrap의 출력값.)
terraform {
  backend "s3" {
    bucket         = "REPLACE_WITH_BOOTSTRAP_STATE_BUCKET"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "REPLACE_WITH_BOOTSTRAP_LOCK_TABLE"
    encrypt        = true
  }
}
