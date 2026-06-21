# dev DNS — domain_name+route53_zone_id 제공 시 EIP로 향하는 A 레코드.
resource "aws_route53_record" "host" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [var.public_ip]
}
