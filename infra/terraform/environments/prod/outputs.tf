output "alb_dns_name" {
  description = "ALB DNS 이름"
  value       = module.alb.dns_name
}

output "app_url" {
  description = "앱 진입 URL(HTTPS — 도메인 필수)"
  value       = "https://${var.domain_name}"
}

output "ecr_repository_url" {
  description = "ECR 리포지토리 URL(CD push 대상)"
  value       = module.ecr.repository_url
}

output "ecs_cluster_name" {
  description = "ECS 클러스터 이름"
  value       = module.ecs.cluster_name
}

output "ecs_service_name" {
  description = "ECS 서비스 이름"
  value       = module.ecs.service_name
}

output "rds_endpoint" {
  description = "RDS 엔드포인트 호스트"
  value       = module.rds.address
}

output "docdb_endpoint" {
  description = "DocumentDB 클러스터 엔드포인트"
  value       = module.documentdb.endpoint
}

output "redis_primary_endpoint" {
  description = "Redis 프라이머리 엔드포인트"
  value       = module.elasticache.primary_endpoint
}

output "cloudfront_domain_name" {
  description = "매물 이미지 CloudFront 기본 도메인(*.cloudfront.net)"
  value       = module.s3_cloudfront.cloudfront_domain_name
}

output "images_cdn_domain" {
  description = "이미지 서빙 도메인(커스텀 별칭 — 강제)"
  value       = module.s3_cloudfront.cdn_domain
}

output "images_bucket_name" {
  description = "매물 이미지 S3 버킷 이름"
  value       = module.s3_cloudfront.bucket_name
}

output "github_actions_role_arn" {
  description = "GitHub Actions 배포 역할 ARN(워크플로 vars.AWS_DEPLOY_ROLE_ARN 에 설정)"
  value       = module.iam.github_actions_role_arn
}

output "app_secret_params_prefix" {
  description = "앱 시크릿 SSM Parameter Store 접두사(GOOGLE/APPLE/SMTP 등 외부 발급값을 채울 대상)"
  value       = module.secrets.param_prefix
}

output "nat_public_ips" {
  description = "NAT 아웃바운드 고정 IP(외부 서비스 allowlist용)"
  value       = module.network.nat_public_ips
}
