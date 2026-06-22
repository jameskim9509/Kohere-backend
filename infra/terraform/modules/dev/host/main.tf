# dev 호스트 — EC2 1대 + EIP + 데이터 EBS attach. user_data가 docker·compose·시크릿(.env)·스크립트를 부트스트랩.
# AMI는 ami_id 미지정 시 최신 AL2023 x86_64(SSM). Caddy 자동 HTTPS(도메인 필수, :80 폴백 없음). (ADR-0021/0022)
# 시크릿(secrets 모듈)·SG·인스턴스 프로파일·EBS 볼륨은 환경 루트에서 주입. SSH 미개방(SSM 전용).
data "aws_ssm_parameter" "al2023_x86" {
  count = var.ami_id == "" ? 1 : 0
  name  = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  ami_id     = var.ami_id != "" ? var.ami_id : data.aws_ssm_parameter.al2023_x86[0].value
  ssm_prefix = "/${var.name_prefix}"
  # Caddy 사이트 주소 — 도메인으로 자동 HTTPS(도메인 필수).
  caddy_site = var.domain_name
}

resource "aws_instance" "host" {
  ami                    = local.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.instance_profile_name

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
    # 시크릿 조회(SSM→.env)는 별도 스크립트로 분리 — 부팅·배포 양쪽에서 재호출(최신값 반영, ADR-0024).
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
  volume_id    = var.volume_id
  instance_id  = aws_instance.host.id
  skip_destroy = true
}
