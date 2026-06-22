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
