output "endpoint" {
  description = "DocumentDB 클러스터(쓰기) 엔드포인트"
  value       = aws_docdb_cluster.this.endpoint
}

output "reader_endpoint" {
  description = "DocumentDB 읽기 엔드포인트"
  value       = aws_docdb_cluster.this.reader_endpoint
}

output "port" {
  description = "DocumentDB 포트"
  value       = var.port
}

output "username" {
  description = "마스터 사용자명"
  value       = var.username
}

output "secret_arn" {
  description = "DocumentDB 시크릿 ARN (키: username/password/host/port/database/uri)"
  value       = aws_secretsmanager_secret.docdb.arn
}

output "cluster_id" {
  description = "DocumentDB 클러스터 식별자(모니터링 DBClusterIdentifier)"
  value       = aws_docdb_cluster.this.cluster_identifier
}
