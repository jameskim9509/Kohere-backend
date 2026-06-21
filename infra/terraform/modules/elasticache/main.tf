# ElastiCache for Redis — refresh 토큰 저장(ADR-0006). 복제 그룹 + Multi-AZ 자동 장애조치로 내구성 확보.
# (ElastiCache는 AOF 대신 복제+Multi-AZ를 내구성 수단으로 권장한다.)

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name_prefix}-redis"
  subnet_ids = var.subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-redis-subnet-group" })
}

resource "aws_elasticache_parameter_group" "this" {
  name   = "${var.name_prefix}-redis7"
  family = "redis7"

  # refresh 토큰 키는 모두 TTL 보유 → 메모리 압박 시 만료 임박 키부터 축출.
  parameter {
    name  = "maxmemory-policy"
    value = var.maxmemory_policy
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-redis7" })
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name_prefix}-redis"
  description          = "Kohere refresh token store"

  engine         = "redis"
  engine_version = var.engine_version
  node_type      = var.node_type
  port           = var.port

  num_cache_clusters         = 1 + var.replicas
  automatic_failover_enabled = var.replicas > 0
  multi_az_enabled           = var.multi_az && var.replicas > 0

  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = var.security_group_ids
  parameter_group_name = aws_elasticache_parameter_group.this.name

  at_rest_encryption_enabled = var.at_rest_encryption
  transit_encryption_enabled = var.transit_encryption

  snapshot_retention_limit = var.snapshot_retention
  maintenance_window       = "mon:18:30-mon:19:30"
  apply_immediately        = false

  tags = merge(var.tags, { Name = "${var.name_prefix}-redis" })
}
