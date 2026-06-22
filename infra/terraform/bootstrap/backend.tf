# bootstrap 자기 자신의 원격 상태(state).
#
# 닭-달걀: 이 구성이 만드는 S3 버킷이 아직 없는 최초 1회는 backend 를 켤 수 없다(첫 init 실패).
# 따라서 2단계로 적용한다.
#   1) 아래 블록을 주석 처리한 채로:  terraform init && terraform apply   # 로컬 state, 버킷 생성
#   2) 아래 블록 주석 해제 + bucket 을 apply 출력값으로 채움
#   3) terraform init -migrate-state                                      # 로컬 state → S3 이전
#
# 공용 버킷을 쓰되 key 를 분리한다(prod 는 prod/terraform.tfstate). 잠금은 S3 native lockfile(use_lockfile).
terraform {
  backend "s3" {
    bucket       = "kohere-tfstate-471597061265" # bootstrap 출력 state_bucket_name
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
