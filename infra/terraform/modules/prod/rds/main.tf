# RDS for MySQL 8.0 — auth·user 트랜잭션 저장소(ADR-0005). 프라이빗 데이터 서브넷, 비공개.
# 시간대 UTC 강제(app도 UTC). 비밀번호는 생성 후 SSM Parameter Store SecureString에 보관.

resource "random_password" "master" {
  length  = 24
  special = true
  # MySQL 마스터 비밀번호 제약: / @ " 공백 불가
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-rds"
  subnet_ids = var.subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-rds-subnet-group" })
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name_prefix}-mysql80"
  family = "mysql8.0"

  parameter {
    name         = "time_zone"
    value        = "UTC"
    apply_method = "immediate"
  }

  parameter {
    name         = "character_set_server"
    value        = "utf8mb4"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "character_set_client"
    value        = "utf8mb4"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "collation_server"
    value        = "utf8mb4_0900_ai_ci"
    apply_method = "pending-reboot"
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-mysql80" })
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn != "" ? var.kms_key_arn : null

  db_name  = var.db_name
  username = var.username
  password = random_password.master.result
  port     = var.port

  vpc_security_group_ids = var.security_group_ids
  db_subnet_group_name   = aws_db_subnet_group.this.name
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  multi_az                = var.multi_az
  backup_retention_period = var.backup_retention_period
  backup_window           = "17:00-18:00" # UTC (KST 02:00-03:00)
  maintenance_window      = "mon:18:30-mon:19:30"

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name_prefix}-mysql-final"
  copy_tags_to_snapshot     = true

  auto_minor_version_upgrade   = true
  performance_insights_enabled = true
  apply_immediately            = false

  tags = merge(var.tags, { Name = "${var.name_prefix}-mysql" })
}

# 마스터 비밀번호를 SSM SecureString 파라미터로 보관(SM 미사용·무료, ADR-0023).
# host/port/db_name은 모듈 출력(비밀 아님)으로 container_environment에 들어가므로 비번만 파라미터로 둔다.
resource "aws_ssm_parameter" "password" {
  name  = "/${var.name_prefix}/SPRING_DATASOURCE_PASSWORD"
  type  = "SecureString"
  value = random_password.master.result
  tags  = merge(var.tags, { Name = "${var.name_prefix}-rds-password" })
}
