# dev 프론트엔드(임대인 웹) 릴리스 아티팩트 버킷 — GitHub Actions가 빌드 산출물을 올리고,
# dev 호스트가 인스턴스 롤로 내려받아 Caddy가 같은 도메인에서 서빙한다(#232).
#
# 콘텐츠 이미지 버킷(shared/s3-cloudfront)과 성격이 다르다 — 저쪽은 CloudFront로 공개 서빙하지만
# 이쪽은 비공개이고 읽는 주체가 dev 호스트 하나뿐이라 CDN도 버킷 정책도 없다.
#
# 프리픽스 둘로 역할이 갈린다:
#   releases/<sha>/  불변. 한 번 올라가면 덮어쓰지 않는다 — 재빌드 없는 롤백이 여기서 나온다.
#   current.txt      이동 포인터. 지금 라이브인 SHA 한 줄. ECR :dev 이동 태그의 대응물이며,
#                    호스트가 심볼릭 링크 교체에 성공한 뒤에만 갱신한다(부팅 복원의 근거).

resource "aws_s3_bucket" "web" {
  bucket = var.bucket_name
  tags   = merge(var.tags, { Name = var.bucket_name })
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "web" {
  bucket = aws_s3_bucket.web.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 버저닝은 두지 않는다 — 릴리스는 SHA 프리픽스로 이미 불변이라 같은 키를 덮어쓸 일이 없다.
resource "aws_s3_bucket_lifecycle_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  # 중단된 멀티파트 업로드 조각은 ListObjects에 보이지 않아 아무도 눈치채지 못한 채 과금된다.
  rule {
    id     = "abort-incomplete-multipart-upload"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = var.abort_incomplete_multipart_days
    }
  }

  # 릴리스 만료 — 이 값이 정하는 것은 "롤백 가능 여부"가 아니라 **즉시·동일 롤백이 보장되는 창**이다.
  # 만료 뒤에도 배포 워크플로가 그 커밋을 재빌드해 복원할 수 있다(느리고, 당시 번들과 바이트 동일 보장은 없다).
  # prefix로 대상을 가르므로 current.txt 는 규칙에 걸릴 수 없다 — 걸리면 부팅 복원이 조용히 죽는다.
  rule {
    id     = "expire-old-releases"
    status = "Enabled"

    filter {
      prefix = "releases/"
    }

    expiration {
      days = var.release_retention_days
    }
  }
}
