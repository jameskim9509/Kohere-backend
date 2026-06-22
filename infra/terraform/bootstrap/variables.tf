variable "project" {
  description = "프로젝트명"
  type        = string
  default     = "kohere"
}

variable "environment" {
  description = "Environment 태그 값 — bootstrap 리소스(상태 버킷·OIDC)는 prod·dev 공용이라 'shared'"
  type        = string
  default     = "shared"
}

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "상태 버킷 이름 — 필수(S3는 전역 유일)"
  type        = string
}
