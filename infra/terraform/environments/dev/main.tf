# Kohere dev 환경 — EC2 1대에 dev 전용 docker-compose(Caddy · app+mysql+mongo+redis).
# ALB·ECS·RDS·DocumentDB·ElastiCache·NAT 없음 — 매니지드는 dev엔 과투자라 비용 최소화([ADR-0021]).
#   접속: EIP → Route53 A 레코드 + Caddy/Let's Encrypt HTTPS(도메인 제공 시).
#   데이터: 전용 암호화 EBS. 시크릿: SSM Parameter Store SecureString(무료·SM 미사용).
#   앱 이미지: ECR(=prod와 동일). 매물 이미지: S3 + CloudFront(shared 모듈).
# prod처럼 서비스별 dev 모듈(network·security·iam·secrets·storage·host·dns·monitoring)로 조립한다.

locals {
  name_prefix = "${var.project}-${var.environment}"

  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

data "aws_caller_identity" "current" {}

# ===== 매물 이미지 (S3 + CloudFront) — prod·dev 공용 =====
module "s3_cloudfront" {
  source = "../../modules/shared/s3-cloudfront"

  name_prefix = local.name_prefix
  tags        = local.common_tags
  account_id  = data.aws_caller_identity.current.account_id
  bucket_name = var.images_bucket_name
}

# ===== 네트워크 (미니 VPC·IGW·public subnet) =====
module "network" {
  source = "../../modules/dev/network"

  name_prefix = local.name_prefix
  tags        = local.common_tags
}

# ===== 보안 그룹 (80/443 + 옵션 DB 포트) =====
module "security" {
  source = "../../modules/dev/security"

  name_prefix      = local.name_prefix
  tags             = local.common_tags
  vpc_id           = module.network.vpc_id
  ingress_cidrs    = var.ingress_cidrs
  db_ingress_cidrs = var.db_ingress_cidrs
}

# ===== IAM (인스턴스 프로파일 — SSM·ECR·파라미터·S3 이미지) =====
module "iam" {
  source = "../../modules/dev/iam"

  name_prefix       = local.name_prefix
  tags              = local.common_tags
  aws_region        = var.aws_region
  account_id        = data.aws_caller_identity.current.account_id
  images_bucket_arn = module.s3_cloudfront.bucket_arn
}

# ===== 시크릿 (SSM Parameter Store SecureString) =====
module "secrets" {
  source = "../../modules/dev/secrets"

  name_prefix         = local.name_prefix
  tags                = local.common_tags
  google_client_id    = var.google_client_id
  apple_client_id     = var.apple_client_id
  smtp_username       = var.smtp_username
  smtp_password       = var.smtp_password
  mysql_password      = var.mysql_password
  mysql_root_password = var.mysql_root_password
  mongo_password      = var.mongo_password
}

# ===== 데이터 EBS (mysql/mongo 영속) =====
module "storage" {
  source = "../../modules/dev/storage"

  name_prefix       = local.name_prefix
  tags              = local.common_tags
  availability_zone = module.network.subnet_az
  size              = var.data_volume_size
}

# ===== 호스트 (EC2 + EIP + EBS attach + user_data 부트스트랩) =====
module "host" {
  source = "../../modules/dev/host"

  name_prefix           = local.name_prefix
  tags                  = local.common_tags
  aws_region            = var.aws_region
  account_id            = data.aws_caller_identity.current.account_id
  subnet_id             = module.network.subnet_id
  security_group_id     = module.security.security_group_id
  instance_profile_name = module.iam.instance_profile_name
  volume_id             = module.storage.volume_id
  instance_type         = var.instance_type
  app_image             = var.app_image
  db_name               = var.db_name
  domain_name           = var.domain_name
  smtp_host             = var.smtp_host
  smtp_port             = var.smtp_port
  mail_from             = var.mail_from
  mysql_username        = var.mysql_username
  mongo_username        = var.mongo_username
  images_bucket         = module.s3_cloudfront.bucket_name
  images_cdn_domain     = module.s3_cloudfront.cloudfront_domain_name

  # 시크릿 파라미터가 먼저 존재해야 부팅 시 refresh-env가 .env로 주입할 수 있다.
  depends_on = [module.secrets]
}

# ===== Route53 A 레코드 (도메인 제공 시) → EIP =====
module "dns" {
  source = "../../modules/dev/dns"

  domain_name     = var.domain_name
  route53_zone_id = var.route53_zone_id
  public_ip       = module.host.public_ip
}

# ===== 모니터링 (CloudWatch 알람 + SNS) =====
module "monitoring" {
  source = "../../modules/dev/monitoring"

  name_prefix = local.name_prefix
  tags        = local.common_tags
  alarm_email = var.alarm_email
  instance_id = module.host.instance_id
}
