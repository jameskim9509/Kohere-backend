variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "subnet_ids" {
  description = "DocumentDB 서브넷 그룹에 쓸 프라이빗 데이터 서브넷 ID 목록"
  type        = list(string)
}

variable "security_group_ids" {
  description = "DocumentDB에 적용할 보안 그룹 ID 목록"
  type        = list(string)
}

variable "engine_version" {
  description = "DocumentDB 엔진 버전(API는 5.0.0 형식 요구)"
  type        = string
  default     = "5.0.0"
}

variable "instance_class" {
  description = "DocumentDB 인스턴스 클래스(최소 db.t3.medium)"
  type        = string
  default     = "db.t3.medium"
}

variable "instance_count" {
  description = "클러스터 인스턴스 수"
  type        = number
  default     = 1
}

variable "backup_retention" {
  description = "자동 백업 보존일"
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "삭제 보호"
  type        = bool
  default     = true
}

variable "username" {
  description = "마스터 사용자명"
  type        = string
  default     = "kohere"
}

variable "database_name" {
  description = "애플리케이션 논리 DB 이름(연결 URI 경로)"
  type        = string
  default     = "kohere"
}

variable "tls" {
  description = "DocumentDB TLS 활성화(enabled/disabled)"
  type        = string
  default     = "enabled"
}

variable "kms_key_arn" {
  description = "스토리지 암호화용 KMS 키 ARN(빈 값이면 AWS 관리형 키)"
  type        = string
  default     = ""
}

variable "recovery_window_days" {
  description = "Secrets Manager 삭제 복구 대기일"
  type        = number
  default     = 7
}

variable "port" {
  description = "DocumentDB 포트"
  type        = number
  default     = 27017
}
