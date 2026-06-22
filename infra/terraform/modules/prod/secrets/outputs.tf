output "param_arns" {
  description = "앱 시크릿 SSM SecureString 파라미터 ARN 맵(키=env 이름) — ECS valueFrom·IAM용"
  value       = { for k, p in aws_ssm_parameter.app : k => p.arn }
}

output "param_prefix" {
  description = "앱 시크릿 파라미터 접두사(/name_prefix)"
  value       = "/${var.name_prefix}"
}
