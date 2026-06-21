variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "secret_arns" {
  description = "ECS 태스크 실행 역할이 읽을 수 있어야 하는 Secrets Manager 시크릿 ARN 목록"
  type        = list(string)
}

variable "ecr_repository_arn" {
  description = "GitHub Actions가 push할 ECR 리포지토리 ARN"
  type        = string
}

variable "github_org" {
  description = "배포 워크플로가 도는 GitHub 조직/사용자"
  type        = string
}

variable "github_repo" {
  description = "배포 워크플로가 도는 GitHub 리포지토리"
  type        = string
}

variable "github_deploy_branch" {
  description = "배포를 허용할 브랜치"
  type        = string
  default     = "main"
}

variable "region" {
  description = "AWS 리전(ARN 구성용)"
  type        = string
}

variable "account_id" {
  description = "AWS 계정 ID(ARN 구성용)"
  type        = string
}

variable "create_github_oidc_provider" {
  description = "GitHub OIDC provider를 새로 생성할지(false면 계정 내 기존 provider 조회)"
  type        = bool
  default     = true
}

variable "kms_key_arns" {
  description = "시크릿 복호화에 필요한 고객 관리형 KMS 키 ARN 목록(기본 AWS 관리형 키면 비워둠)"
  type        = list(string)
  default     = []
}

variable "images_bucket_arn" {
  description = "앱(ECS 태스크 역할)이 업로드할 매물 이미지 S3 버킷 ARN. 빈 값이면 S3 권한 미부여"
  type        = string
  default     = ""
}
