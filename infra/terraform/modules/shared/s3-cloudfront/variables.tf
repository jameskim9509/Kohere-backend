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
  description = "매물 이미지 버킷 이름(빈 값이면 name_prefix-listing-images-<account_id>)"
  type        = string
  default     = ""
}

variable "account_id" {
  description = "버킷 이름 유일성 보장용 AWS 계정 ID"
  type        = string
}

variable "price_class" {
  description = "CloudFront 가격 등급(PriceClass_200은 아시아 포함)"
  type        = string
  default     = "PriceClass_200"
}

variable "enable_versioning" {
  description = "S3 버전 관리 활성화"
  type        = bool
  default     = true
}

# ----- 커스텀 도메인(별칭 + ACM) — 필수. HTTPS·커스텀 도메인 강제 -----
variable "domain_aliases" {
  description = "CloudFront 별칭 도메인 목록(예: [\"cdn.kohere.app\"]) — 필수"
  type        = list(string)
}

variable "acm_certificate_arn" {
  description = "별칭에 쓸 ACM 인증서 ARN(**반드시 us-east-1**) — 필수"
  type        = string
}

variable "route53_zone_id" {
  description = "별칭 A/AAAA(alias) 레코드를 만들 Route53 호스팅 영역 ID — 필수"
  type        = string
}
