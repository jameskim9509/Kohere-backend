# Terraform 원격 상태 백엔드 부트스트랩 — S3(state). 잠금은 S3 native lockfile(use_lockfile, TF 1.10+) → DynamoDB 불요.
# environments/{prod,dev} 보다 먼저 1회 apply 한다. (최초엔 로컬 state → 이후 backend.tf로 S3 이전: migrate-state.)

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  tags = { Name = var.state_bucket_name }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# TLS 강제 — 비TLS 요청 거부.
data "aws_iam_policy_document" "state_tls" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = data.aws_iam_policy_document.state_tls.json
}

# 상태 잠금은 S3 native lockfile(backend "s3" 의 use_lockfile=true)로 처리한다 — 별도 DynamoDB 테이블이 필요 없다.
# S3가 조건부 쓰기(If-None-Match)로 <key>.tflock 객체를 만들어 동시 실행을 직렬화한다.
