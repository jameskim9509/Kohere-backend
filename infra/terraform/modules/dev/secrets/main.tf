# dev 앱 시크릿 — SSM Parameter Store SecureString (SM 미사용·무료, ADR-0023).
# 자동 생성(앱 부팅 필수): JWT_SECRET·REFRESH_PEPPER·EMAIL_PEPPER. 나머지는 외부 발급 + DB 자격증명.
# 호스트가 인스턴스 프로파일로 부팅 시 .env에 주입. 변경 반영은 배포(refresh-env + reconcile-db, ADR-0024/0025).
resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}
resource "random_password" "refresh_pepper" {
  length  = 32
  special = false
}
resource "random_password" "email_pepper" {
  length  = 32
  special = false
}

locals {
  ssm_prefix = "/${var.name_prefix}"
  # 키 → 값 (자동 생성 + 외부 발급 + DB 자격증명). SecureString 기본 키(aws/ssm) 사용.
  secure_params = {
    JWT_SECRET       = random_password.jwt_secret.result
    REFRESH_PEPPER   = random_password.refresh_pepper.result
    EMAIL_PEPPER     = random_password.email_pepper.result
    GOOGLE_CLIENT_ID = var.google_client_id
    APPLE_CLIENT_ID  = var.apple_client_id
    SMTP_USERNAME    = var.smtp_username
    SMTP_PASSWORD    = var.smtp_password
    # DB 자격증명(dev — 자가호스팅 컨테이너). 외부 노출 가능성 있어 SSM SecureString으로 관리.
    MYSQL_PASSWORD      = var.mysql_password
    MYSQL_ROOT_PASSWORD = var.mysql_root_password
    MONGO_PASSWORD      = var.mongo_password
  }
}

resource "aws_ssm_parameter" "secure" {
  for_each = local.secure_params
  name     = "${local.ssm_prefix}/${each.key}"
  type     = "SecureString"
  value    = each.value
  tags     = merge(var.tags, { Name = "${var.name_prefix}-${lower(each.key)}" })
}
