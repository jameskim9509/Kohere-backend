output "task_execution_role_arn" {
  description = "ECS 태스크 실행 역할 ARN"
  value       = aws_iam_role.task_execution.arn
}

output "task_role_arn" {
  description = "ECS 태스크 역할 ARN(앱 런타임)"
  value       = aws_iam_role.task.arn
}

output "github_actions_role_arn" {
  description = "GitHub Actions 배포 역할 ARN(워크플로 secrets/vars에 설정)"
  value       = aws_iam_role.github_actions.arn
}

output "github_oidc_provider_arn" {
  description = "GitHub OIDC provider ARN"
  value       = try(aws_iam_openid_connect_provider.github[0].arn, data.aws_iam_openid_connect_provider.github[0].arn)
}
