variable "name_prefix" {
  description = "리소스 이름 접두사 (예: kohere-dev)"
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

variable "vpc_cidr" {
  description = "dev 전용 VPC CIDR (prod와 분리)"
  type        = string
  default     = "10.1.0.0/16"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입. x86 — ECR 앱 이미지가 amd64(prod ECS X86_64)"
  type        = string
  default     = "t3.medium"
}

variable "ami_id" {
  description = "빈 값이면 SSM으로 최신 AL2023 x86_64 조회"
  type        = string
  default     = ""
}

variable "app_image" {
  description = "ECR 앱 이미지 URI(:tag 포함). CI가 push한 dev 이미지"
  type        = string
}

variable "app_port" {
  description = "앱 컨테이너 포트(Caddy 뒤 내부 포트)"
  type        = number
  default     = 8080
}

variable "data_volume_size" {
  description = "데이터 EBS 크기(GB) — mysql/mongo 영속(Redis는 인메모리)"
  type        = number
  default     = 20
}

variable "ingress_cidrs" {
  description = "80/443 인바운드 허용 CIDR (dev는 편의상 개방 가능)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "db_name" {
  description = "MySQL·Mongo 논리 DB 이름"
  type        = string
  default     = "kohere"
}

# ===== DB 자격증명 (SSM SecureString) + 외부 접속 (dev 한정) =====
# 최초 init 후의 사용자명·비번 변경은 배포 시 reconcile-db.sh가 live DB에 회전 적용한다(데이터 보존, ADR-0025).
variable "mysql_username" {
  description = "MySQL 앱 사용자명(앱이 이 계정으로 접속). 시크릿 아님 → compose 변수"
  type        = string
  default     = "kohere"
}

variable "mysql_password" {
  description = "MySQL 앱 사용자(kohere) 비밀번호 — SSM SecureString. 외부 노출 시 강한 값 권장"
  type        = string
  default     = "kohere"
  sensitive   = true
}

variable "mysql_root_password" {
  description = "MySQL root 비밀번호 — SSM SecureString(외부 관리도구용)"
  type        = string
  default     = "kohere"
  sensitive   = true
}

variable "mongo_username" {
  description = "MongoDB root 사용자명(인증 활성화)"
  type        = string
  default     = "kohere"
}

variable "mongo_password" {
  description = "MongoDB root 비밀번호 — SSM SecureString. URL-safe 값 권장(접속 URI에 포함)"
  type        = string
  default     = "kohere"
  sensitive   = true
}

variable "db_ingress_cidrs" {
  description = "DB 포트(MySQL 3306·Mongo 27017) 외부 접속 허용 CIDR. 빈 목록=미개방(앱 내부 접속만). 전체 개방(0.0.0.0/0·::/0) 금지(검증) — 본인 IP/32로 제한"
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.db_ingress_cidrs, "0.0.0.0/0") && !contains(var.db_ingress_cidrs, "::/0")
    error_message = "db_ingress_cidrs 에 전체 개방(0.0.0.0/0·::/0)은 금지한다. 본인 IP/32 등으로 제한하라."
  }
}

variable "domain_name" {
  description = "dev 도메인(예: dev.kohere.app). 제공 시 Route53 A 레코드 + Let's Encrypt HTTPS, 없으면 HTTP"
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Route53 호스팅 영역 ID (domain_name과 함께 A 레코드 생성)"
  type        = string
  default     = ""
}

variable "le_email" {
  description = "Let's Encrypt 등록 이메일(acme-companion)"
  type        = string
  default     = ""
}

# ===== 앱 시크릿 (SSM Parameter Store SecureString — SM 미사용·무료) =====
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

variable "smtp_host" {
  description = "실 SMTP 호스트(dev — MailHog 없음. 예: email-smtp.ap-northeast-2.amazonaws.com)"
  type        = string
  default     = ""
}

variable "smtp_port" {
  description = "SMTP 포트"
  type        = number
  default     = 587
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

variable "mail_from" {
  description = "발신 이메일 주소"
  type        = string
  default     = "noreply@kohere.app"
}

variable "alarm_email" {
  description = "CloudWatch 알람 SNS 이메일 구독(빈 값이면 미구독)"
  type        = string
  default     = ""
}

# ===== 매물 이미지(S3 + CloudFront) — dev env가 모듈 출력을 주입 =====
variable "images_bucket" {
  description = "매물 이미지 S3 버킷 이름"
  type        = string
  default     = ""
}

variable "images_bucket_arn" {
  description = "매물 이미지 S3 버킷 ARN(앱 업로드 IAM용)"
  type        = string
  default     = ""
}

variable "images_cdn_domain" {
  description = "CloudFront 도메인(이미지 URL 베이스)"
  type        = string
  default     = ""
}
