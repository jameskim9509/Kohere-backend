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
