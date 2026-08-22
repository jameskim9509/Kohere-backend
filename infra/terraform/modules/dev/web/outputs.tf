output "bucket_name" {
  description = "프론트 릴리스 아티팩트 버킷 이름(CI 업로드 대상 · 호스트 다운로드 원본)"
  value       = aws_s3_bucket.web.bucket
}

output "bucket_arn" {
  description = "프론트 릴리스 아티팩트 버킷 ARN(IAM 정책 스코프)"
  value       = aws_s3_bucket.web.arn
}
