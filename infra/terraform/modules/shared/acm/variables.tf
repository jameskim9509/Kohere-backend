variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "domain_name" {
  description = "인증서 기본 도메인 (예: api.kohere.app)"
  type        = string
}

variable "route53_zone_id" {
  description = "DNS 검증 레코드를 생성할 Route53 호스팅 영역 ID"
  type        = string
}

variable "subject_alternative_names" {
  description = "추가 도메인(SAN) 목록"
  type        = list(string)
  default     = []
}
