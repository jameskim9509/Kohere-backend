output "ssm_prefix" {
  description = "SSM 파라미터 접두사(/<name_prefix>)"
  value       = local.ssm_prefix
}

output "parameter_arns" {
  description = "생성된 SecureString 파라미터 ARN 맵(호스트 의존성 명시용)"
  value       = { for k, p in aws_ssm_parameter.secure : k => p.arn }
}
