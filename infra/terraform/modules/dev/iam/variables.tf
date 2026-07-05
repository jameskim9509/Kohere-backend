variable "name_prefix" {
  description = "리소스 이름 접두사. SSM 파라미터 접두사(/<name_prefix>)도 이 값 기준"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "aws_region" {
  description = "AWS 리전(파라미터 ARN 구성)"
  type        = string
}

variable "account_id" {
  description = "AWS 계정 ID(파라미터 ARN 구성)"
  type        = string
}

variable "images_bucket_arn" {
  description = "콘텐츠 이미지 S3 버킷 ARN(앱 업로드 권한). 빈 값이면 S3 권한 미부여"
  type        = string
  default     = ""
}
