# Kohere dev 환경 — EC2 1대에 dev 전용 docker-compose(Caddy · app+mysql+mongo+redis).
# ALB·ECS·RDS·DocumentDB·ElastiCache·NAT 없음 — dev에는 매니지드 스택이 과투자라 비용 최소화([ADR-0021]).
#   접속: EIP → Route53 A 레코드 + Caddy/Let's Encrypt HTTPS(도메인 제공 시).
#   데이터: 전용 암호화 EBS. 시크릿: SSM Parameter Store SecureString(무료·SM 미사용).
#   앱 이미지: ECR(=prod와 동일). 매물 이미지: S3 + CloudFront(prod와 동일 모듈).

locals {
  name_prefix = "${var.project}-${var.environment}"

  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

data "aws_caller_identity" "current" {}

# ===== 매물 이미지 (S3 + CloudFront) — prod와 동일 모듈 =====
module "s3_cloudfront" {
  source = "../../modules/shared/s3-cloudfront"

  name_prefix = local.name_prefix
  tags        = local.common_tags
  account_id  = data.aws_caller_identity.current.account_id
  bucket_name = var.images_bucket_name
}

# ===== dev 호스트 (EC2 1대 docker-compose) =====
module "dev_host" {
  source = "../../modules/dev/dev-host"

  name_prefix      = local.name_prefix
  tags             = local.common_tags
  aws_region       = var.aws_region
  account_id       = data.aws_caller_identity.current.account_id
  app_image        = var.app_image
  instance_type    = var.instance_type
  data_volume_size = var.data_volume_size
  ingress_cidrs    = var.ingress_cidrs
  db_name          = var.db_name

  # DB 자격증명(SSM SecureString) + 외부 접속 ingress (dev 한정)
  mysql_username      = var.mysql_username
  mysql_password      = var.mysql_password
  mysql_root_password = var.mysql_root_password
  mongo_username      = var.mongo_username
  mongo_password      = var.mongo_password
  db_ingress_cidrs    = var.db_ingress_cidrs

  # 노출 / HTTPS
  domain_name     = var.domain_name
  route53_zone_id = var.route53_zone_id
  le_email        = var.le_email

  # 시크릿 (SSM Parameter Store SecureString)
  google_client_id = var.google_client_id
  apple_client_id  = var.apple_client_id
  smtp_host        = var.smtp_host
  smtp_port        = var.smtp_port
  smtp_username    = var.smtp_username
  smtp_password    = var.smtp_password
  mail_from        = var.mail_from

  # 알람
  alarm_email = var.alarm_email

  # 매물 이미지
  images_bucket     = module.s3_cloudfront.bucket_name
  images_bucket_arn = module.s3_cloudfront.bucket_arn
  images_cdn_domain = module.s3_cloudfront.cloudfront_domain_name
}
