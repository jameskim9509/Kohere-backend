output "cluster_name" {
  description = "ECS 클러스터 이름"
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ECS 클러스터 ARN"
  value       = aws_ecs_cluster.this.arn
}

output "service_name" {
  description = "ECS 서비스 이름"
  value       = aws_ecs_service.this.name
}

output "task_definition_arn" {
  description = "초기 태스크 정의 ARN"
  value       = aws_ecs_task_definition.this.arn
}

output "log_group_name" {
  description = "CloudWatch 로그 그룹 이름"
  value       = aws_cloudwatch_log_group.app.name
}

output "container_name" {
  description = "컨테이너 이름(CD가 --container-name 으로 사용)"
  value       = local.container_name
}
