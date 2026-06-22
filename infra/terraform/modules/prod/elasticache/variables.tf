variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "subnet_ids" {
  description = "캐시 서브넷 그룹에 쓸 프라이빗 데이터 서브넷 ID 목록"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Redis에 적용할 보안 그룹 ID 목록"
  type        = list(string)
}

variable "node_type" {
  description = "캐시 노드 타입"
  type        = string
  default     = "cache.t4g.micro"
}

variable "engine_version" {
  description = "Redis 엔진 버전"
  type        = string
  default     = "7.1"
}

variable "replicas" {
  description = "프라이머리당 복제본 수(1이면 1 primary + 1 replica)"
  type        = number
  default     = 1
}

variable "multi_az" {
  description = "Multi-AZ 자동 장애조치(복제본>0일 때만 유효)"
  type        = bool
  default     = true
}

variable "transit_encryption" {
  description = "전송 구간 암호화(true면 auth token + Spring SSL 설정 필요 — MVP 범위 밖)"
  type        = bool
  default     = false
}

variable "at_rest_encryption" {
  description = "저장 구간 암호화"
  type        = bool
  default     = true
}

variable "maxmemory_policy" {
  description = "maxmemory-policy (refresh 토큰은 TTL 보유 → volatile-ttl 권장)"
  type        = string
  default     = "volatile-ttl"
}

variable "snapshot_retention" {
  description = "스냅샷 보존일"
  type        = number
  default     = 7
}

variable "port" {
  description = "Redis 포트"
  type        = number
  default     = 6379
}
