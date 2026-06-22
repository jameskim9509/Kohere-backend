# dev DNS — EIP로 향하는 A 레코드(도메인·zone 필수).
resource "aws_route53_record" "host" {
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [var.public_ip]
}
