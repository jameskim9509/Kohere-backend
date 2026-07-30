variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "discord_webhook_url" {
  description = "Discord 웹훅 URL(빈 값이면 Discord 알림 미구성). SNS→Lambda가 알람을 변환·전달"
  type        = string
  default     = ""
  sensitive   = true
}

variable "instance_id" {
  description = "감시 대상 EC2 인스턴스 ID"
  type        = string
}

variable "log_group_name" {
  description = "수집량을 감시할 앱 Log Group 이름(logs 모듈). 빈 값이면 수집량 알람 미구성"
  type        = string
  default     = ""
}

variable "log_ingestion_daily_limit_bytes" {
  description = <<-EOT
    일 로그 수집량 알람 임계(바이트). 기본 200MB — ADR-0038이 정한 상한이다.
    비용 기준으로 정했다: CloudWatch Logs는 수집 GB당 과금이라 상한이 곧 월 비용 상한이고,
    200MB/일 ≈ 월 6GB ≈ $5로 dev 호스트(~$17/월)의 1/3이다. 1GB/일이면 월 ~$24로 호스트 비용을 넘는다.
    AWS는 Log Group 수집량 하드 리밋을 제공하지 않으므로 이 알람은 차단이 아니라 조기 경보다.
  EOT
  type        = number
  default     = 209715200 # 200 * 1024 * 1024
}
