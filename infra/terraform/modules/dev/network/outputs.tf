output "vpc_id" {
  description = "dev VPC ID"
  value       = aws_vpc.this.id
}

output "subnet_id" {
  description = "public subnet ID(호스트 배치)"
  value       = aws_subnet.public.id
}

output "subnet_az" {
  description = "public subnet 가용영역(EBS 볼륨 동일 AZ 배치용)"
  value       = aws_subnet.public.availability_zone
}
