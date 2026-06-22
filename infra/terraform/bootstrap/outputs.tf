output "state_bucket_name" {
  description = "원격 상태 S3 버킷 이름 → environments/prod/backend.tf 의 bucket"
  value       = aws_s3_bucket.state.bucket
}

output "region" {
  description = "리전"
  value       = var.aws_region
}

output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN — environments/{prod,dev} 가 data 로 조회(참고용)"
  value       = aws_iam_openid_connect_provider.github.arn
}
