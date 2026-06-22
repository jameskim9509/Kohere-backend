# 매물 이미지 호스팅 — 비공개 S3 + CloudFront(OAC). 클라이언트가 CloudFront에서 직접 로드.
# 서빙 경로에 앱은 없다(읽기는 CloudFront 직결, 앱은 URL만 저장). 이 모듈은 버킷·CDN·읽기 정책만
# 정의하며, 앱의 업로드(PutObject) 권한은 iam 모듈이 태스크 역할에 부여한다(images_bucket_arn 연결 시).

resource "aws_s3_bucket" "images" {
  bucket = var.bucket_name
  tags   = merge(var.tags, { Name = var.bucket_name })
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

  # 커스텀 도메인 별칭을 us-east-1 ACM 인증서로 TLS 종단(HTTPS·커스텀 도메인 강제 — 인증서 필수).
  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-images-cdn" })
}

# 별칭 도메인 → CloudFront alias 레코드(A/AAAA). 별칭마다 생성(도메인·zone 필수).
resource "aws_route53_record" "alias_a" {
  for_each = toset(var.domain_aliases)

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
  for_each = toset(var.domain_aliases)

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
