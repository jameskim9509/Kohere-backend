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
  description = "보안 그룹을 생성할 VPC ID"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR (참고용)"
  type        = string
}

variable "app_port" {
  description = "애플리케이션 컨테이너 포트"
  type        = number
  default     = 8080
}

variable "alb_ingress_cidrs" {
  description = "ALB 인바운드를 허용할 CIDR 목록"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
