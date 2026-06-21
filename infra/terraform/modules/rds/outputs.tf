output "address" {
  description = "RDS 엔드포인트 호스트"
  value       = aws_db_instance.this.address
}

output "port" {
  description = "RDS 포트"
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "데이터베이스 이름"
  value       = var.db_name
}

output "username" {
  description = "마스터 사용자명"
  value       = var.username
}

output "secret_arn" {
  description = "RDS 자격증명 시크릿 ARN (키: username/password/host/port/dbname)"
  value       = aws_secretsmanager_secret.rds.arn
}

output "instance_id" {
  description = "RDS 인스턴스 식별자(모니터링 DBInstanceIdentifier)"
  value       = aws_db_instance.this.identifier
}
