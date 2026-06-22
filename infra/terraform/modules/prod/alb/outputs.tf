output "alb_arn" {
  description = "ALB ARN"
  value       = aws_lb.this.arn
}

output "alb_arn_suffix" {
  description = "ALB ARN suffix(CloudWatch 차원 LoadBalancer)"
  value       = aws_lb.this.arn_suffix
}

output "dns_name" {
  description = "ALB DNS 이름"
  value       = aws_lb.this.dns_name
}

output "zone_id" {
  description = "ALB 호스팅 영역 ID(Route53 alias용)"
  value       = aws_lb.this.zone_id
}

output "target_group_arn" {
  description = "타깃 그룹 ARN(ECS 서비스 등록용)"
  value       = aws_lb_target_group.this.arn
}

output "target_group_arn_suffix" {
  description = "타깃 그룹 ARN suffix(CloudWatch 차원 TargetGroup)"
  value       = aws_lb_target_group.this.arn_suffix
}
