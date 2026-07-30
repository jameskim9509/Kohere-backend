# dev 호스트 IAM — SSM(관리자 접속·파라미터 읽기) + ECR pull + (옵션) 이미지 버킷 S3.
# 인스턴스 프로파일로 EC2에 부여. 시크릿은 SSM Parameter Store에서 부팅 시 .env로 주입(ADR-0023).
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm" # SecureString 기본 키(무료) — Decrypt 권한 한정용
}

locals {
  ssm_prefix = "/${var.name_prefix}"
}

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

# 시크릿 파라미터(이 접두사)만 읽기 + SecureString 복호화(aws/ssm 키) + (옵션) 이미지 버킷.
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
  # 콘텐츠 이미지 버킷 읽기/쓰기(앱이 인스턴스 역할로 업로드) — images_bucket_arn 제공 시에만.
  dynamic "statement" {
    for_each = var.images_bucket_arn != "" ? [1] : []
    content {
      sid       = "ImagesBucketRW"
      actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
      resources = [var.images_bucket_arn, "${var.images_bucket_arn}/*"]
    }
  }

  # CloudWatch Agent가 /opt/kohere/logs/app.json을 tail해 보낼 권한(ADR-0038).
  # 관리형 CloudWatchAgentServerPolicy(logs:* + 전 Log Group)를 쓰지 않고 이 Log Group 하나로 스코프한다.
  # Log Group과 보존기간은 Terraform(logs 모듈)이 소유하므로 CreateLogGroup·PutRetentionPolicy는 주지 않는다 —
  # Agent가 임의 그룹을 만들거나 보존 정책을 덮을 수 없다.
  dynamic "statement" {
    for_each = var.log_group_arn != "" ? [1] : []
    content {
      sid       = "AppLogsToCloudWatch"
      actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
      resources = [var.log_group_arn, "${trimsuffix(var.log_group_arn, ":*")}:log-stream:*"]
    }
  }
}

resource "aws_iam_role_policy" "params" {
  name   = "${var.name_prefix}-params"
  role   = aws_iam_role.host.name
  policy = data.aws_iam_policy_document.params.json
}

resource "aws_iam_instance_profile" "host" {
  name = "${var.name_prefix}-host"
  role = aws_iam_role.host.name
}
