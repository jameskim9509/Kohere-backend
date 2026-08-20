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
  description = "콘텐츠 이미지 S3 버킷 ARN(s3-cloudfront 모듈). 앱이 인스턴스 역할로 업로드한다"
  type        = string
}

variable "log_group_arn" {
  description = "앱 로그 Log Group ARN(logs 모듈). CloudWatch Agent 권한을 이 그룹으로만 스코프한다"
  type        = string
}

variable "web_bucket_arn" {
  description = <<-EOT
    프론트 릴리스 아티팩트 S3 버킷 ARN(web 모듈, #232).
    릴리스는 읽기만, 쓰기는 current.txt 한 객체뿐이다 — 호스트가 릴리스를 만들거나 지울 수 없다.
  EOT
  type        = string
}
