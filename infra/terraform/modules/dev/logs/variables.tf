variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "log_group_name" {
  description = "앱 로그 Log Group 이름(ADR-0038 — dev는 /<project>/<environment>/app)"
  type        = string
}

variable "retention_in_days" {
  description = "로그 보존 기간(일). 무기한(0) 금지 — 방치하면 비용이 선형으로 누적된다(ADR-0038)"
  type        = number
  default     = 30

  validation {
    condition     = var.retention_in_days > 0
    error_message = "retention_in_days는 0(무기한)일 수 없다. ADR-0038이 무기한 보존을 금지한다."
  }
}
