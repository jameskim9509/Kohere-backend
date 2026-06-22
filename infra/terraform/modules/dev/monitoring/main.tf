# dev 모니터링 — 단일 박스라 SPOF. 상태 점검 실패·CPU 알람을 SNS로 받아 Discord로 통보.
# Discord 웹훅은 SNS를 직접 못 받아 SNS→Lambda 변환 경로를 쓴다(ADR-0027). discord_webhook_url 비면 미구성.
resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"
  tags = merge(var.tags, { Name = "${var.name_prefix}-alerts" })
}

resource "aws_cloudwatch_metric_alarm" "status_check" {
  alarm_name          = "${var.name_prefix}-status-check-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "breaching"
  alarm_description   = "dev 호스트 상태 점검 실패(인스턴스/시스템 장애·소실)"
  dimensions          = { InstanceId = var.instance_id }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  tags                = var.tags
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "${var.name_prefix}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  treat_missing_data  = "notBreaching"
  alarm_description   = "dev 호스트 CPU 사용률 높음"
  dimensions          = { InstanceId = var.instance_id }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  tags                = var.tags
}

# ===== Discord 웹훅 알림 — CloudWatch 알람 → SNS → Lambda → Discord (ADR-0027) =====
# Discord 웹훅은 SNS HTTPS 구독을 못 받는다(구독 확인 핸드셰이크·메시지 포맷 불일치) → Lambda가 변환·전달.
# discord_webhook_url 이 비어 있으면 전부 미생성(count=0).
locals {
  discord_enabled = var.discord_webhook_url != "" ? 1 : 0
}

# Lambda 소스(의존성 0·urllib)를 plan 시점에 zip — 별도 빌드/업로드 불필요.
data "archive_file" "discord" {
  count       = local.discord_enabled
  type        = "zip"
  source_file = "${path.module}/lambda/discord_notify.py"
  output_path = "${path.module}/lambda/discord_notify.zip"
}

data "aws_iam_policy_document" "discord_assume" {
  count = local.discord_enabled
  statement {
    actions = ["sts:AssumeRole"]
    effect  = "Allow"
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "discord" {
  count              = local.discord_enabled
  name               = "${var.name_prefix}-discord-notify"
  assume_role_policy = data.aws_iam_policy_document.discord_assume[0].json
  tags               = var.tags
}

# CloudWatch Logs 쓰기만 — 최소 권한.
resource "aws_iam_role_policy_attachment" "discord_logs" {
  count      = local.discord_enabled
  role       = aws_iam_role.discord[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "discord" {
  count            = local.discord_enabled
  function_name    = "${var.name_prefix}-discord-notify"
  role             = aws_iam_role.discord[0].arn
  runtime          = "python3.12"
  handler          = "discord_notify.handler"
  filename         = data.archive_file.discord[0].output_path
  source_code_hash = data.archive_file.discord[0].output_base64sha256
  timeout          = 10

  environment {
    variables = {
      DISCORD_WEBHOOK_URL = var.discord_webhook_url
    }
  }

  tags = var.tags
}

resource "aws_lambda_permission" "discord_sns" {
  count         = local.discord_enabled
  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.discord[0].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.alerts.arn
}

resource "aws_sns_topic_subscription" "discord" {
  count     = local.discord_enabled
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.discord[0].arn
}
