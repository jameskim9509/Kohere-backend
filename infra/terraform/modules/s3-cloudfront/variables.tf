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
