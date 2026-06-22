variable "name_prefix" {
  description = "리소스 이름 접두사. SSM 파라미터 접두사(/<name_prefix>) 기준"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "aws_region" {
  description = "AWS 리전 (ECR·SSM)"
  type        = string
}

variable "account_id" {
  description = "AWS 계정 ID (ECR 레지스트리 호스트)"
  type        = string
}

# ----- 인프라 주입(다른 dev 모듈 출력) -----
variable "subnet_id" {
  description = "배치할 public subnet ID(network 모듈)"
  type        = string
}

variable "security_group_id" {
  description = "호스트 보안 그룹 ID(security 모듈)"
  type        = string
}

variable "instance_profile_name" {
  description = "인스턴스 프로파일 이름(iam 모듈)"
  type        = string
}

variable "volume_id" {
  description = "attach할 데이터 EBS 볼륨 ID(storage 모듈)"
  type        = string
}

# ----- 인스턴스 -----
variable "instance_type" {
  description = "EC2 인스턴스 타입. x86 — ECR 앱 이미지가 amd64. 기본 t3.small=2vCPU/2GB"
  type        = string
  default     = "t3.small"
}

# ----- 앱 / compose 렌더 -----
variable "app_image" {
  description = "ECR 앱 이미지 URI(:tag 포함). CI가 push한 dev 이미지"
  type        = string
}

variable "db_name" {
  description = "MySQL·Mongo 논리 DB 이름"
  type        = string
  default     = "kohere"
}

variable "domain_name" {
  description = "dev 도메인(Caddy 자동 HTTPS) — 필수"
  type        = string
}

variable "smtp_host" {
  description = "실 SMTP 호스트(dev — MailHog 없음)"
  type        = string
  default     = ""
}

variable "smtp_port" {
  description = "SMTP 포트"
  type        = number
  default     = 587
}

variable "mail_from" {
  description = "발신 이메일 주소"
  type        = string
  default     = "noreply@kohere.app"
}

variable "mysql_username" {
  description = "MySQL 앱 사용자명(compose·reconcile)"
  type        = string
  default     = "kohere"
}

variable "mongo_username" {
  description = "MongoDB root 사용자명(compose·reconcile)"
  type        = string
  default     = "kohere"
}

# ----- 매물 이미지(S3 + CloudFront, shared 모듈 출력) -----
variable "images_bucket" {
  description = "매물 이미지 S3 버킷 이름(앱 env)"
  type        = string
  default     = ""
}

variable "images_cdn_domain" {
  description = "CloudFront 도메인(이미지 URL 베이스, 앱 env)"
  type        = string
  default     = ""
}
