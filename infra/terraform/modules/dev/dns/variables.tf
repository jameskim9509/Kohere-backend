variable "domain_name" {
  description = "dev 도메인(예: dev.kohere.app). 비우면 레코드 미생성"
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Route53 호스팅 영역 ID. 비우면 레코드 미생성"
  type        = string
  default     = ""
}

variable "public_ip" {
  description = "A 레코드 대상 공인 IP(호스트 EIP)"
  type        = string
}
