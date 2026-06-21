output "alb_sg_id" {
  description = "ALB 보안 그룹 ID"
  value       = aws_security_group.alb.id
}

output "app_sg_id" {
  description = "ECS 태스크 보안 그룹 ID"
  value       = aws_security_group.app.id
}

output "rds_sg_id" {
  description = "RDS 보안 그룹 ID"
  value       = aws_security_group.rds.id
}

output "docdb_sg_id" {
  description = "DocumentDB 보안 그룹 ID"
  value       = aws_security_group.docdb.id
}

output "redis_sg_id" {
  description = "Redis 보안 그룹 ID"
  value       = aws_security_group.redis.id
}
