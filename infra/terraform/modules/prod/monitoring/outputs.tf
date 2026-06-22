output "sns_topic_arn" {
  description = "알람 SNS 토픽 ARN"
  value       = aws_sns_topic.alerts.arn
}

output "alarm_names" {
  description = "생성된 CloudWatch 알람 이름 목록"
  value = [
    aws_cloudwatch_metric_alarm.alb_5xx.alarm_name,
    aws_cloudwatch_metric_alarm.alb_target_5xx.alarm_name,
    aws_cloudwatch_metric_alarm.alb_unhealthy_hosts.alarm_name,
    aws_cloudwatch_metric_alarm.ecs_cpu.alarm_name,
    aws_cloudwatch_metric_alarm.ecs_memory.alarm_name,
    aws_cloudwatch_metric_alarm.rds_cpu.alarm_name,
    aws_cloudwatch_metric_alarm.rds_free_storage.alarm_name,
    aws_cloudwatch_metric_alarm.docdb_cpu.alarm_name,
    aws_cloudwatch_metric_alarm.redis_memory.alarm_name,
  ]
}
