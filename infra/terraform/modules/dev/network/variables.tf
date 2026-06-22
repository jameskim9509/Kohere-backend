variable "name_prefix" {
  description = "리소스 이름 접두사 (예: kohere-dev)"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "vpc_cidr" {
  description = "dev 전용 VPC CIDR (prod와 분리)"
  type        = string
  default     = "10.1.0.0/16"
}
