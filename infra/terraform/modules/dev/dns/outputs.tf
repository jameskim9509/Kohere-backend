output "fqdn" {
  description = "생성된 A 레코드 FQDN(없으면 빈 문자열)"
  value       = length(aws_route53_record.host) > 0 ? aws_route53_record.host[0].fqdn : ""
}
