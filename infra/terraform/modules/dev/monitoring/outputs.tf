output "topic_arn" {
  description = "알람 SNS 토픽 ARN"
  value       = aws_sns_topic.alerts.arn
}
