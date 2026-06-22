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
  description = "타깃 그룹을 생성할 VPC ID"
  type        = string
}

variable "public_subnet_ids" {
  description = "ALB를 배치할 퍼블릭 서브넷 ID 목록"
  type        = list(string)
}

variable "security_group_ids" {
  description = "ALB 보안 그룹 ID 목록"
  type        = list(string)
}

variable "app_port" {
  description = "타깃(컨테이너) 포트"
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "헬스 체크 경로"
  type        = string
  default     = "/actuator/health"
}

variable "certificate_arn" {
  description = "HTTPS 리스너용 ACM 인증서 ARN — 필수(HTTPS 강제)"
  type        = string
}

variable "idle_timeout" {
  description = "ALB idle timeout(초)"
  type        = number
  default     = 60
}

variable "deregistration_delay" {
  description = "타깃 등록 해제 지연(초)"
  type        = number
  default     = 30
}

variable "ssl_policy" {
  description = "HTTPS 리스너 SSL 정책"
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "enable_deletion_protection" {
  description = "ALB 삭제 보호"
  type        = bool
  default     = false
}
