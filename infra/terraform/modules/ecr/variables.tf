variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "repo_name" {
  description = "ECR 리포지토리 이름 (앱 이미지)"
  type        = string
}

variable "image_retention_count" {
  description = "보관할 최신 이미지 수(초과분은 lifecycle policy로 만료)"
  type        = number
  default     = 20
}

variable "scan_on_push" {
  description = "푸시 시 이미지 취약점 스캔 여부"
  type        = bool
  default     = true
}

variable "image_tag_mutability" {
  description = "이미지 태그 변경 가능 여부 (MUTABLE 또는 IMMUTABLE)"
  type        = string
  default     = "MUTABLE"
}
