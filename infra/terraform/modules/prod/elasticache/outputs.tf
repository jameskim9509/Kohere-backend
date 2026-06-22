output "primary_endpoint" {
  description = "Redis 프라이머리(쓰기) 엔드포인트 주소"
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "reader_endpoint" {
  description = "Redis 읽기 엔드포인트 주소"
  value       = aws_elasticache_replication_group.this.reader_endpoint_address
}

output "port" {
  description = "Redis 포트"
  value       = var.port
}

output "replication_group_id" {
  description = "복제 그룹 ID(모니터링 ReplicationGroupId)"
  value       = aws_elasticache_replication_group.this.replication_group_id
}
