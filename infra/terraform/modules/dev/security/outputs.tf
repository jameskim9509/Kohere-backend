output "security_group_id" {
  description = "dev 호스트 보안 그룹 ID"
  value       = aws_security_group.host.id
}
