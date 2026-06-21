# Amazon DocumentDB — listing(+찜·최근본)·diagnosis 문서 저장소(ADR-0005). 프라이빗 데이터 서브넷.
# 비밀번호는 영숫자만(special=false)으로 생성 → URI 인코딩 없이 안전하게 임베드.
# SPRING_DATA_MONGODB_URI는 Mongo 드라이버 배선(추후) 시 그대로 사용 — 현재는 forward-looking.

resource "random_password" "master" {
  length  = 24
  special = false
}

resource "aws_docdb_subnet_group" "this" {
  name       = "${var.name_prefix}-docdb"
  subnet_ids = var.subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-docdb-subnet-group" })
}

resource "aws_docdb_cluster_parameter_group" "this" {
  name   = "${var.name_prefix}-docdb50"
  family = "docdb5.0"

  parameter {
    name  = "tls"
    value = var.tls
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-docdb50" })
}

resource "aws_docdb_cluster" "this" {
  cluster_identifier = "${var.name_prefix}-docdb"
  engine             = "docdb"
  engine_version     = var.engine_version
  port               = var.port

  master_username = var.username
  master_password = random_password.master.result

  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.this.name
  db_subnet_group_name            = aws_docdb_subnet_group.this.name
  vpc_security_group_ids          = var.security_group_ids

  storage_encrypted = true
  kms_key_id        = var.kms_key_arn != "" ? var.kms_key_arn : null

  backup_retention_period      = var.backup_retention
  preferred_backup_window      = "17:00-18:00"
  preferred_maintenance_window = "mon:18:30-mon:19:30"

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name_prefix}-docdb-final"

  tags = merge(var.tags, { Name = "${var.name_prefix}-docdb" })
}

resource "aws_docdb_cluster_instance" "this" {
  count              = var.instance_count
  identifier         = "${var.name_prefix}-docdb-${count.index}"
  cluster_identifier = aws_docdb_cluster.this.id
  instance_class     = var.instance_class
  engine             = "docdb"

  tags = merge(var.tags, { Name = "${var.name_prefix}-docdb-${count.index}" })
}

locals {
  docdb_tls_flag = var.tls == "enabled" ? "true" : "false"
  docdb_uri = format(
    "mongodb://%s:%s@%s:%d/%s?tls=%s&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false",
    var.username,
    random_password.master.result,
    aws_docdb_cluster.this.endpoint,
    var.port,
    var.database_name,
    local.docdb_tls_flag,
  )
}

# 연결 URI(비번 임베드)를 SSM SecureString 파라미터로 보관(SM 미사용·무료, ADR-0023).
resource "aws_ssm_parameter" "uri" {
  name  = "/${var.name_prefix}/SPRING_DATA_MONGODB_URI"
  type  = "SecureString"
  value = local.docdb_uri
  tags  = merge(var.tags, { Name = "${var.name_prefix}-docdb-uri" })
}
