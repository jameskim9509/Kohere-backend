output "public_ip" {
  description = "dev 호스트 EIP(고정 공인 IP) — Route53 A 레코드 대상"
  value       = aws_eip.host.public_ip
}

output "instance_id" {
  description = "dev 호스트 EC2 인스턴스 ID(SSM 접속용)"
  value       = aws_instance.host.id
}

output "app_url" {
  description = "앱 접속 URL(도메인 제공 시 HTTPS, 아니면 EIP HTTP)"
  value       = var.domain_name != "" ? "https://${var.domain_name}" : "http://${aws_eip.host.public_ip}"
}

output "vpc_id" {
  description = "dev VPC ID"
  value       = aws_vpc.this.id
}

output "sns_topic_arn" {
  description = "dev 알람 SNS 토픽 ARN"
  value       = aws_sns_topic.alerts.arn
}
