# 매물 이미지 호스팅 — 비공개 S3 + CloudFront(OAC). 클라이언트가 CloudFront에서 직접 로드.
# 서빙 경로에 앱은 없다(읽기는 CloudFront 직결, 앱은 URL만 저장). 이 모듈은 버킷·CDN·읽기 정책만
# 정의하며, 앱의 업로드(PutObject) 권한은 iam 모듈이 태스크 역할에 부여한다(images_bucket_arn 연결 시).

locals {
  bucket = var.bucket_name != "" ? var.bucket_name : "${var.name_prefix}-listing-images-${var.account_id}"
}

resource "aws_s3_bucket" "images" {
  bucket = local.bucket
  tags   = merge(var.tags, { Name = local.bucket })
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "images" {
  bucket = aws_s3_bucket.images.id
  versioning_configuration {
    status = var.enable_versioning ? "Enabled" : "Suspended"
  }
}

resource "aws_cloudfront_origin_access_control" "images" {
  name                              = "${var.name_prefix}-images-oac"
  description                       = "OAC for listing images bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "images" {
  enabled         = true
  comment         = "${var.name_prefix} listing images"
  price_class     = var.price_class
  is_ipv6_enabled = true
  aliases         = var.domain_aliases

  origin {
    domain_name              = aws_s3_bucket.images.bucket_regional_domain_name
    origin_id                = "s3-images"
    origin_access_control_id = aws_cloudfront_origin_access_control.images.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-images"
    viewer_protocol_policy = "redirect-to-https"
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 별칭(커스텀 도메인)이 있으면 us-east-1 ACM 인증서로 TLS 종단, 없으면 *.cloudfront.net 기본 인증서.
  viewer_certificate {
    cloudfront_default_certificate = var.acm_certificate_arn == ""
    acm_certificate_arn            = var.acm_certificate_arn != "" ? var.acm_certificate_arn : null
    ssl_support_method             = var.acm_certificate_arn != "" ? "sni-only" : null
    minimum_protocol_version       = var.acm_certificate_arn != "" ? "TLSv1.2_2021" : null
  }

  # 별칭이 있으면 반드시 ACM 인증서(us-east-1)가 있어야 한다(CloudFront 불변식) — plan 단계에서 조기 실패시킨다.
  lifecycle {
    precondition {
      condition     = length(var.domain_aliases) == 0 || var.acm_certificate_arn != ""
      error_message = "domain_aliases 사용 시 acm_certificate_arn(us-east-1 ACM)이 필수다. 환경 레이어는 cdn_domain_name과 route53_zone_id를 함께 설정하거나, 인증서 ARN을 직접 주입하라."
    }
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-images-cdn" })
}

# 별칭 도메인 → CloudFront alias 레코드(A/AAAA). route53_zone_id 제공 시에만 생성(없으면 DNS 외부 관리).
resource "aws_route53_record" "alias_a" {
  for_each = var.route53_zone_id != "" ? toset(var.domain_aliases) : toset([])

  zone_id = var.route53_zone_id
  name    = each.value
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.images.domain_name
    zone_id                = aws_cloudfront_distribution.images.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "alias_aaaa" {
  for_each = var.route53_zone_id != "" ? toset(var.domain_aliases) : toset([])

  zone_id = var.route53_zone_id
  name    = each.value
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.images.domain_name
    zone_id                = aws_cloudfront_distribution.images.hosted_zone_id
    evaluate_target_health = false
  }
}

# CloudFront(OAC) → S3 GetObject 만 허용.
data "aws_iam_policy_document" "bucket" {
  statement {
    sid       = "AllowCloudFrontOAC"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.images.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.bucket.json
}
