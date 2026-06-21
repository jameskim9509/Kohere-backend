variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "aws_region" {
  description = "awslogs 드라이버용 리전"
  type        = string
}

variable "app_subnet_ids" {
  description = "태스크를 배치할 프라이빗 앱 서브넷 ID 목록"
  type        = list(string)
}

variable "security_group_ids" {
  description = "태스크 ENI 보안 그룹 ID 목록"
  type        = list(string)
}

variable "target_group_arn" {
  description = "ALB 타깃 그룹 ARN"
  type        = string
}

variable "container_image" {
  description = "컨테이너 이미지(리포지토리 URL + 태그)"
  type        = string
}

variable "container_port" {
  description = "컨테이너 포트"
  type        = number
  default     = 8080
}

variable "task_cpu" {
  description = "태스크 CPU 단위"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "태스크 메모리(MB)"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "원하는 태스크 수(초기값 — 이후 오토스케일링/CD가 관리)"
  type        = number
  default     = 2
}

variable "autoscaling_min" {
  description = "오토스케일링 최소 태스크 수"
  type        = number
  default     = 2
}

variable "autoscaling_max" {
  description = "오토스케일링 최대 태스크 수"
  type        = number
  default     = 6
}

variable "cpu_target" {
  description = "타깃 추적 오토스케일링 CPU 목표(%)"
  type        = number
  default     = 60
}

variable "task_execution_role_arn" {
  description = "ECS 태스크 실행 역할 ARN"
  type        = string
}

variable "task_role_arn" {
  description = "ECS 태스크 역할 ARN"
  type        = string
}

variable "container_environment" {
  description = "평문 환경변수 목록"
  type = list(object({
    name  = string
    value = string
  }))
  default = []
}

variable "container_secrets" {
  description = "Secrets Manager 주입 시크릿 목록(valueFrom)"
  type = list(object({
    name      = string
    valueFrom = string
  }))
  default = []
}

variable "log_retention_days" {
  description = "CloudWatch 로그 보존일"
  type        = number
  default     = 30
}

variable "enable_container_insights" {
  description = "Container Insights 활성화(기본 true, 현행 보존). 비용 절감 시 false"
  type        = bool
  default     = true
}

variable "health_check_path" {
  description = "헬스 체크 경로(참고용)"
  type        = string
  default     = "/actuator/health"
}

variable "enable_execute_command" {
  description = "ECS Exec 활성화(디버깅용 셸 접속)"
  type        = bool
  default     = true
}

variable "container_name" {
  description = "컨테이너 이름(빈 값이면 name_prefix-app)"
  type        = string
  default     = ""
}

variable "assign_public_ip" {
  description = "태스크 ENI에 공인 IP 부여 여부(프라이빗 서브넷이면 false)"
  type        = bool
  default     = false
}
