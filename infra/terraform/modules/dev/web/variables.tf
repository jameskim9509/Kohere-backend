variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "bucket_name" {
  description = "프론트 릴리스 아티팩트 S3 버킷 이름(전역 유일)"
  type        = string
}

variable "release_retention_days" {
  description = <<-EOT
    releases/ 아래 릴리스를 보관할 일수. "롤백 가능 여부"가 아니라 즉시·동일 롤백이 보장되는 창을 정한다 —
    만료된 SHA로 롤백하면 배포 워크플로가 그 커밋을 재빌드해 복원한다(느리고 바이트 동일 보장 없음).
    늘리는 방향은 부작용이 없지만, 줄이면 초과 나이의 릴리스가 실제로 삭제되어 그만큼 즉시 롤백 창이 사라진다.
  EOT
  type        = number
  default     = 90
}

variable "abort_incomplete_multipart_days" {
  description = "중단된 멀티파트 업로드 조각을 정리할 일수"
  type        = number
  default     = 7
}
