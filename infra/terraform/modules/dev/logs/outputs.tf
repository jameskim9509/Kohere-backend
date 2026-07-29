output "log_group_name" {
  description = "앱 로그 Log Group 이름(Agent 설정·Logs Insights 쿼리 대상)"
  value       = aws_cloudwatch_log_group.app.name
}

output "log_group_arn" {
  description = "앱 로그 Log Group ARN(IAM 인라인 정책 스코프용)"
  value       = aws_cloudwatch_log_group.app.arn
}
