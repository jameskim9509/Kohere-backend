output "volume_id" {
  description = "데이터 EBS 볼륨 ID(호스트가 attach)"
  value       = aws_ebs_volume.data.id
}
