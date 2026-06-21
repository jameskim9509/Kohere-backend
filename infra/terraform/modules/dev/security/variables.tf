variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "vpc_id" {
  description = "SG를 둘 VPC ID"
  type        = string
}

variable "ingress_cidrs" {
  description = "80/443 인바운드 허용 CIDR (dev는 편의상 개방 가능)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "db_ingress_cidrs" {
  description = "DB 포트(MySQL 3306·Mongo 27017) 외부 접속 허용 CIDR. 빈 목록=미개방. 전체 개방(0.0.0.0/0·::/0) 금지(검증) — 본인 IP/32로 제한"
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.db_ingress_cidrs, "0.0.0.0/0") && !contains(var.db_ingress_cidrs, "::/0")
    error_message = "db_ingress_cidrs 에 전체 개방(0.0.0.0/0·::/0)은 금지한다. 본인 IP/32 등으로 제한하라."
  }
}
