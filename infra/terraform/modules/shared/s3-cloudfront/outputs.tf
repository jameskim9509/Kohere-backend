output "bucket_name" {
  description = "이미지 버킷 이름"
  value       = aws_s3_bucket.images.bucket
}

output "bucket_arn" {
  description = "이미지 버킷 ARN"
  value       = aws_s3_bucket.images.arn
}

output "cloudfront_domain_name" {
  description = "CloudFront 배포 기본 도메인(*.cloudfront.net)"
  value       = aws_cloudfront_distribution.images.domain_name
}

output "cdn_domain" {
  description = "이미지 서빙 도메인(커스텀 별칭 — 강제)"
  value       = var.domain_aliases[0]
}

output "distribution_id" {
  description = "CloudFront 배포 ID"
  value       = aws_cloudfront_distribution.images.id
}

output "distribution_arn" {
  description = "CloudFront 배포 ARN"
  value       = aws_cloudfront_distribution.images.arn
}
