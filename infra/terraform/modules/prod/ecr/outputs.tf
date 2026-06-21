output "repository_url" {
  description = "ECR 리포지토리 URL (이미지 태그용)"
  value       = aws_ecr_repository.this.repository_url
}

output "repository_arn" {
  description = "ECR 리포지토리 ARN"
  value       = aws_ecr_repository.this.arn
}

output "repository_name" {
  description = "ECR 리포지토리 이름"
  value       = aws_ecr_repository.this.name
}
