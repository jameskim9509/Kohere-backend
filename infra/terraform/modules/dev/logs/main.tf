# dev 앱 로그 수집 대상 — CloudWatch Log Group(ADR-0038 "수집·반출").
# 앱은 파일까지만 책임진다(/logs/app.json). 호스트의 CloudWatch Agent가 그 파일을 tail해 여기로 보낸다 —
# 로그 "내용"(JSON·MDC)과 "전송 경로"가 직교라, 앱은 AWS SDK를 물지 않는다.
#
# prod는 ECS awslogs 드라이버가 /ecs/<name_prefix>에 직접 쓴다(modules/prod/ecs). 네이밍이 환경별로
# 갈리는 것은 prod가 ECS 관례를 이미 커밋했기 때문이며, 통일은 prod CD 연결 시점에 재검토한다.
#
# Log Group을 Terraform이 소유하므로 Agent에는 CreateLogGroup·PutRetentionPolicy 권한을 주지 않는다
# (iam 모듈의 인라인 정책은 CreateLogStream·PutLogEvents·DescribeLogStreams만 — 관리형 logs:* 미사용).
resource "aws_cloudwatch_log_group" "app" {
  name              = var.log_group_name
  retention_in_days = var.retention_in_days
  tags              = merge(var.tags, { Name = var.log_group_name })
}
