output "certificate_arn" {
  description = "검증 완료된 ACM 인증서 ARN"
  value       = aws_acm_certificate_validation.this.certificate_arn
}
