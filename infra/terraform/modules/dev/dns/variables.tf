variable "domain_name" {
  description = "dev 도메인(예: dev.kohere.app) — 필수"
  type        = string
}

variable "route53_zone_id" {
  description = "Route53 호스팅 영역 ID — 필수"
  type        = string
}

variable "public_ip" {
  description = "A 레코드 대상 공인 IP(호스트 EIP)"
  type        = string
}
