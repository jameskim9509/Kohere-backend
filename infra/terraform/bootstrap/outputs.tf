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

output "ecr_repository_url" {
  description = "앱 이미지 ECR URL — CI push·compose pull 대상(environments 는 이름으로 data 조회)"
  value       = aws_ecr_repository.app.repository_url
}

output "ecr_repository_arn" {
  description = "앱 이미지 ECR ARN — 배포 역할 IAM 정책용(참고용)"
  value       = aws_ecr_repository.app.arn
}

output "ecr_repository_name" {
  description = "앱 이미지 ECR 리포지토리 이름"
  value       = aws_ecr_repository.app.name
}
