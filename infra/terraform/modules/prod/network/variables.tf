variable "name_prefix" {
  description = "리소스 이름 접두사 (예: kohere-prod)"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "사용할 가용 영역(AZ) 수"
  type        = number
  default     = 2
}

variable "single_nat_gateway" {
  description = "true면 NAT 게이트웨이 1개만 생성(비용 절감), false면 AZ당 1개(가용성)"
  type        = bool
  default     = true
}

variable "enable_interface_endpoints" {
  description = "ECR/Secrets Manager/Logs/SSM 인터페이스 VPC 엔드포인트 생성 여부"
  type        = bool
  default     = true
}

variable "app_port" {
  description = "애플리케이션 컨테이너 포트(참고용)"
  type        = number
  default     = 8080
}
