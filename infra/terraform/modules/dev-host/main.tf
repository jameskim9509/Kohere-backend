# dev 호스트 — EC2 1대에 dev 전용 docker-compose(Caddy · app+mysql+mongo+redis)를 올린다.
# mailhog 없음(로컬 전용). HTTPS는 Caddy(자동 Let's Encrypt, ADR-0022). EIP→Route53 A 레코드.
# 시크릿은 SSM Parameter Store SecureString(무료·SM 미사용), 인스턴스 프로파일로 부팅 시 .env에 주입.
# 데이터(mysql/mongo)는 전용 암호화 EBS(Redis는 인메모리). 관리자 접속은 SSM 전용(SSH 미개방). (ADR-0021)

data "aws_ssm_parameter" "al2023_x86" {
  count = var.ami_id == "" ? 1 : 0
  name  = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm" # SecureString 기본 키(무료) — Decrypt 권한 한정용
}

locals {
  ami_id     = var.ami_id != "" ? var.ami_id : data.aws_ssm_parameter.al2023_x86[0].value
  ssm_prefix = "/${var.name_prefix}"
  # Caddy 사이트 주소 — 도메인 있으면 자동 HTTPS, 없으면 :80(HTTP) 폴백.
  caddy_site = var.domain_name != "" ? var.domain_name : ":80"
}

# ===== 미니 VPC (prod와 분리, 단일 public subnet) =====
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 0)
  map_public_ip_on_launch = true
  tags                    = merge(var.tags, { Name = "${var.name_prefix}-public" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-rt-public" })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ===== 보안 그룹 — 80/443(Caddy) + (옵션) DB 포트 3306/27017. SSH 미개방(SSM 전용) =====
resource "aws_security_group" "host" {
  name        = "${var.name_prefix}-host-sg"
  description = "dev host: 80/443 inbound (Caddy), all egress"
  vpc_id      = aws_vpc.this.id
  tags        = merge(var.tags, { Name = "${var.name_prefix}-host-sg" })
}

resource "aws_security_group_rule" "http_in" {
  type              = "ingress"
  description       = "HTTP(80) — ACME 챌린지·HTTPS 리다이렉트"
  security_group_id = aws_security_group.host.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = var.ingress_cidrs
}

resource "aws_security_group_rule" "https_in" {
  type              = "ingress"
  description       = "HTTPS(443)"
  security_group_id = aws_security_group.host.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = var.ingress_cidrs
}

resource "aws_security_group_rule" "egress_all" {
  type              = "egress"
  description       = "all"
  security_group_id = aws_security_group.host.id
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

# (옵션) DB 외부 접속 — db_ingress_cidrs 지정 시에만 개방. 컨테이너는 host 포트 발행(compose ports), SG가 게이트.
resource "aws_security_group_rule" "mysql_in" {
  count             = length(var.db_ingress_cidrs) > 0 ? 1 : 0
  type              = "ingress"
  description       = "MySQL(3306) 외부 접속 — db_ingress_cidrs로 제한"
  security_group_id = aws_security_group.host.id
  from_port         = 3306
  to_port           = 3306
  protocol          = "tcp"
  cidr_blocks       = var.db_ingress_cidrs
}

resource "aws_security_group_rule" "mongo_in" {
  count             = length(var.db_ingress_cidrs) > 0 ? 1 : 0
  type              = "ingress"
  description       = "MongoDB(27017) 외부 접속 — db_ingress_cidrs로 제한"
  security_group_id = aws_security_group.host.id
  from_port         = 27017
  to_port           = 27017
  protocol          = "tcp"
  cidr_blocks       = var.db_ingress_cidrs
}

# ===== 앱 시크릿 — SSM Parameter Store SecureString (SM 미사용·무료) =====
# 자동 생성(앱 부팅 필수): JWT_SECRET·REFRESH_PEPPER·EMAIL_PEPPER
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
  # 키 → 값 (자동 생성 + 외부 발급). SecureString 기본 키(aws/ssm) 사용.
  secure_params = {
    JWT_SECRET     = random_password.jwt_secret.result
    REFRESH_PEPPER = random_password.refresh_pepper.result
    EMAIL_PEPPER   = random_password.email_pepper.result
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

# ===== IAM — SSM(관리자·파라미터) + ECR pull =====
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "host" {
  name               = "${var.name_prefix}-host"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-host" })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_pull" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# 시크릿 파라미터(이 접두사)만 읽기 + SecureString 복호화(aws/ssm 키).
data "aws_iam_policy_document" "params" {
  statement {
    sid       = "ReadDevParams"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.aws_region}:${var.account_id}:parameter${local.ssm_prefix}/*"]
  }
  statement {
    sid       = "DecryptSecureString"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
  # 매물 이미지 버킷 읽기/쓰기(앱이 인스턴스 역할로 업로드) — images_bucket_arn 제공 시에만.
  dynamic "statement" {
    for_each = var.images_bucket_arn != "" ? [1] : []
    content {
      sid       = "ImagesBucketRW"
      actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
      resources = [var.images_bucket_arn, "${var.images_bucket_arn}/*"]
    }
  }
}

resource "aws_iam_role_policy" "params" {
  name   = "${var.name_prefix}-params"
  role   = aws_iam_role.host.id
  policy = data.aws_iam_policy_document.params.json
}

resource "aws_iam_instance_profile" "host" {
  name = "${var.name_prefix}-host"
  role = aws_iam_role.host.name
}

# ===== 데이터 EBS (암호화, 인스턴스 교체에도 잔존) =====
resource "aws_ebs_volume" "data" {
  availability_zone = aws_subnet.public.availability_zone
  size              = var.data_volume_size
  type              = "gp3"
  encrypted         = true
  tags              = merge(var.tags, { Name = "${var.name_prefix}-data" })

  lifecycle {
    prevent_destroy = true
  }
}

# ===== EC2 + EIP =====
resource "aws_instance" "host" {
  ami                    = local.ami_id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.host.id]
  iam_instance_profile   = aws_iam_instance_profile.host.name

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  user_data_replace_on_change = false
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    aws_region = var.aws_region
    account_id = var.account_id
    # 시크릿 조회(SSM→.env)는 별도 스크립트로 분리 — 부팅·배포 양쪽에서 재호출(최신값 반영).
    refresh_env = templatefile("${path.module}/refresh-env.sh.tftpl", {
      aws_region     = var.aws_region
      ssm_prefix     = local.ssm_prefix
      mysql_username = var.mysql_username
      mongo_username = var.mongo_username
      db_name        = var.db_name
    })
    # DB 자격증명 reconcile(데이터 보존 회전, ADR-0025) — Terraform 변수 없는 정적 스크립트.
    reconcile_db = templatefile("${path.module}/reconcile-db.sh.tftpl", {})
    # compose·Caddyfile은 standalone 템플릿으로 렌더해 주입(가시성·CI 재사용).
    caddyfile = templatefile("${path.module}/Caddyfile.tftpl", {
      caddy_site = local.caddy_site
    })
    compose_yml = templatefile("${path.module}/docker-compose.yml.tftpl", {
      db_name           = var.db_name
      app_image         = var.app_image
      smtp_host         = var.smtp_host
      smtp_port         = var.smtp_port
      mail_from         = var.mail_from
      images_bucket     = var.images_bucket
      images_cdn_domain = var.images_cdn_domain
      mongo_username    = var.mongo_username
      mysql_username    = var.mysql_username
    })
  })

  lifecycle {
    ignore_changes = [ami]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-host" })
}

resource "aws_eip" "host" {
  domain   = "vpc"
  instance = aws_instance.host.id
  tags     = merge(var.tags, { Name = "${var.name_prefix}-eip" })
}

resource "aws_volume_attachment" "data" {
  device_name  = "/dev/sdf"
  volume_id    = aws_ebs_volume.data.id
  instance_id  = aws_instance.host.id
  skip_destroy = true
}

# ===== Route53 A 레코드 (domain_name+zone_id 제공 시) → EIP =====
resource "aws_route53_record" "host" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.host.public_ip]
}

# ===== CloudWatch — 단일 박스 다운 통보 =====
resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"
  tags = merge(var.tags, { Name = "${var.name_prefix}-alerts" })
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "status_check" {
  alarm_name          = "${var.name_prefix}-status-check-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "breaching"
  alarm_description   = "dev 호스트 상태 점검 실패(인스턴스/시스템 장애·소실)"
  dimensions          = { InstanceId = aws_instance.host.id }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  tags                = var.tags
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "${var.name_prefix}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  treat_missing_data  = "notBreaching"
  alarm_description   = "dev 호스트 CPU 사용률 높음"
  dimensions          = { InstanceId = aws_instance.host.id }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  tags                = var.tags
}
