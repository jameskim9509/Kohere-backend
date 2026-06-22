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

variable "ecr_repository" {
  description = "앱 이미지 ECR 리포지토리 이름 — dev·prod 공유(bootstrap 단일 생성, 환경들은 data 조회)"
  type        = string
  default     = "kohere-backend"
}

variable "ecr_image_retention_count" {
  description = "ECR 보관 최신 이미지 수(초과분은 lifecycle policy로 만료)"
  type        = number
  default     = 20
}
