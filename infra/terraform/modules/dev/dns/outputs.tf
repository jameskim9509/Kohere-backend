output "fqdn" {
  description = "생성된 A 레코드 FQDN"
  value       = aws_route53_record.host.fqdn
}
