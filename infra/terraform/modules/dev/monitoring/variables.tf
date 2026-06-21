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
  description = "CloudWatch 알람 SNS 이메일 구독(빈 값이면 미구독)"
  type        = string
  default     = ""
}

variable "instance_id" {
  description = "감시 대상 EC2 인스턴스 ID"
  type        = string
}
