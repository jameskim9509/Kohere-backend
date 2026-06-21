output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "VPC CIDR 블록"
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "퍼블릭 서브넷 ID 목록 (ALB)"
  value       = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  description = "프라이빗 앱 서브넷 ID 목록 (ECS)"
  value       = aws_subnet.app[*].id
}

output "data_subnet_ids" {
  description = "프라이빗 데이터 서브넷 ID 목록 (RDS·DocumentDB·ElastiCache)"
  value       = aws_subnet.data[*].id
}

output "availability_zones" {
  description = "사용한 가용 영역 목록"
  value       = local.azs
}

output "nat_public_ips" {
  description = "NAT 게이트웨이 공인 IP 목록(아웃바운드 고정 IP — 외부 allowlist용)"
  value       = aws_eip.nat[*].public_ip
}
