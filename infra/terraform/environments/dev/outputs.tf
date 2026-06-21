output "app_url" {
  description = "앱 접속 URL(도메인 제공 시 HTTPS)"
  value       = module.dev_host.app_url
}

output "public_ip" {
  description = "dev 호스트 EIP(Route53 A 레코드 대상)"
  value       = module.dev_host.public_ip
}

output "instance_id" {
  description = "dev 호스트 EC2 인스턴스 ID(SSM 접속용)"
  value       = module.dev_host.instance_id
}

output "images_bucket" {
  description = "매물 이미지 S3 버킷"
  value       = module.s3_cloudfront.bucket_name
}

output "images_cdn_domain" {
  description = "매물 이미지 CloudFront 도메인"
  value       = module.s3_cloudfront.cloudfront_domain_name
}

output "github_deploy_role_arn" {
  description = "GitHub Actions가 assume할 배포 역할 ARN(리포 Variables AWS_DEPLOY_ROLE_ARN 에 설정)"
  value       = aws_iam_role.github_deploy.arn
}
