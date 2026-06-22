# dev 보안 그룹 — 80/443(Caddy) + (옵션) DB 포트 3306/27017. SSH 미개방(SSM 전용).
resource "aws_security_group" "host" {
  name        = "${var.name_prefix}-host-sg"
  description = "dev host: 80/443 inbound (Caddy), all egress"
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name_prefix}-host-sg" })
}

resource "aws_security_group_rule" "http_in" {
  type              = "ingress"
  description       = "HTTP(80) - ACME challenge / HTTPS redirect"
  security_group_id = aws_security_group.host.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = var.ingress_cidrs
}

resource "aws_security_group_rule" "https_in" {
  type              = "ingress"
  description       = "HTTPS(443)"
  security_group_id = aws_security_group.host.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = var.ingress_cidrs
}

resource "aws_security_group_rule" "egress_all" {
  type              = "egress"
  description       = "all"
  security_group_id = aws_security_group.host.id
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

# (옵션) DB 외부 접속 — db_ingress_cidrs 지정 시에만 개방. 컨테이너는 host 포트 발행(compose ports), SG가 게이트.
resource "aws_security_group_rule" "mysql_in" {
  count             = length(var.db_ingress_cidrs) > 0 ? 1 : 0
  type              = "ingress"
  description       = "MySQL(3306) external access - limited by db_ingress_cidrs"
  security_group_id = aws_security_group.host.id
  from_port         = 3306
  to_port           = 3306
  protocol          = "tcp"
  cidr_blocks       = var.db_ingress_cidrs
}

resource "aws_security_group_rule" "mongo_in" {
  count             = length(var.db_ingress_cidrs) > 0 ? 1 : 0
  type              = "ingress"
  description       = "MongoDB(27017) external access - limited by db_ingress_cidrs"
  security_group_id = aws_security_group.host.id
  from_port         = 27017
  to_port           = 27017
  protocol          = "tcp"
  cidr_blocks       = var.db_ingress_cidrs
}
