variable "name_prefix" {
  description = "리소스 이름 접두사. SSM 파라미터 접두사(/<name_prefix>) 기준"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "google_client_id" {
  description = "Google OIDC audience"
  type        = string
  default     = ""
  sensitive   = true
}

variable "apple_client_id" {
  description = "Apple OIDC audience"
  type        = string
  default     = ""
  sensitive   = true
}

variable "smtp_username" {
  description = "SMTP 사용자명"
  type        = string
  default     = ""
  sensitive   = true
}

variable "smtp_password" {
  description = "SMTP 비밀번호"
  type        = string
  default     = ""
  sensitive   = true
}

variable "mysql_password" {
  description = "MySQL 앱 사용자 비밀번호"
  type        = string
  default     = "kohere"
  sensitive   = true
}

variable "mysql_root_password" {
  description = "MySQL root 비밀번호"
  type        = string
  default     = "kohere"
  sensitive   = true
}

variable "mongo_password" {
  description = "MongoDB root 비밀번호"
  type        = string
  default     = "kohere"
  sensitive   = true
}
