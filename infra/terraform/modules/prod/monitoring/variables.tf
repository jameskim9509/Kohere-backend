variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "alarm_email" {
  description = "알람 구독 이메일(빈 값이면 구독 생성 안 함 — 수동 구독). Discord와 병행 가능(다중 채널)"
  type        = string
  default     = ""
}

variable "discord_webhook_url" {
  description = "Discord 웹훅 URL(빈 값이면 Discord 알림 미구성). SNS→Lambda가 알람을 변환·전달(ADR-0027)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix"
  type        = string
}

variable "target_group_arn_suffix" {
  description = "타깃 그룹 ARN suffix"
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS 클러스터 이름"
  type        = string
}

variable "ecs_service_name" {
  description = "ECS 서비스 이름"
  type        = string
}

variable "rds_instance_id" {
  description = "RDS 인스턴스 식별자"
  type        = string
}

variable "docdb_cluster_id" {
  description = "DocumentDB 클러스터 식별자"
  type        = string
}

variable "redis_replication_group_id" {
  description = "ElastiCache 복제 그룹 ID"
  type        = string
}
