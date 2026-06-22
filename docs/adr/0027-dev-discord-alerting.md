# ADR-0027. dev 알람 통보는 Discord 웹훅으로 한다(SNS→Lambda 포워더)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0027 |
| 작성자 | James Kim |
| 작성일 | 2026-06-22 |
| 관련 문서 | [ADR-0021](./0021-cost-optimization-profile.md)(dev 단일 EC2 compose), [modules/dev/monitoring](../../infra/terraform/modules/dev/monitoring/main.tf), [discord_notify.py](../../infra/terraform/modules/dev/monitoring/lambda/discord_notify.py) |

## Status

Accepted

## Context

dev는 단일 EC2라 SPOF다([ADR-0021](./0021-cost-optimization-profile.md)). `modules/dev/monitoring` 이 EC2 `StatusCheckFailed`·`CPUUtilization` **CloudWatch 알람**을 만들어 **SNS 토픽**에 publish한다. 기존 통보 채널은 SNS **이메일 구독**뿐이었는데, 팀 협업 채널이 Discord라 **Discord로 일원화**하려 한다.

팀 협업이 Discord라 알람도 Discord로 받고 싶다. 문제: **SNS는 Discord 웹훅을 직접 구독할 수 없다.**

- SNS의 HTTP/HTTPS 구독은 **구독 확인 핸드셰이크**(SubscriptionConfirmation 토큰 echo)를 요구하는데 Discord 웹훅은 응답하지 않는다.
- SNS가 보내는 JSON 봉투는 Discord가 기대하는 포맷(`{"content":...}` / `embeds`)과 **다르다**.
- AWS Chatbot은 Slack·Teams만 지원(**Discord 미지원**).

## Decision

**CloudWatch 알람 → SNS → Lambda(포워더) → Discord 웹훅** 경로로 한다.

- **Lambda 변환기**: Python 3.12 + 표준 `urllib`(의존성 0). SNS 메시지(CloudWatch 알람 JSON)를 파싱해 **Discord 임베드**로 변환(상태별 색상 ALARM=빨강/OK=초록/INSUFFICIENT_DATA=노랑, Reason 필드 등) 후 웹훅에 POST. 소스는 `lambda/discord_notify.py`.
- **100% Terraform**: `archive_file` 로 plan 시점에 zip을 만들어 배포(수동 빌드·업로드 없음). Lambda·IAM 역할(로그 권한만)·SNS→Lambda 구독·`lambda_permission` 까지 전부 코드. 생성 zip은 `.gitignore` 제외.
- **단일화**: 기존 SNS **이메일 구독은 제거**하고 Discord로 일원화한다(SNS 토픽은 유지 — Discord Lambda가 구독). 모든 Discord 리소스는 `discord_webhook_url != ""` 일 때만 생성(`count`) — 비우면 알람은 CloudWatch 콘솔에만 남고 푸시 통보는 없다.
- **웹훅 URL**: tfvars `discord_webhook_url`(sensitive) → Lambda 환경변수로 주입. Discord 웹훅은 민감도가 낮아(최악: 채널에 메시지 게시) dev에선 SSM 대신 환경변수로 충분.
- **prod 무관**: prod monitoring은 별도(ECS·ALB·RDS 알람). 이 결정은 dev 한정.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| SNS → HTTPS 직접 Discord | 리소스 최소 | 구독 확인 핸드셰이크 미응답·포맷 불일치로 **동작 불가** | 기술적으로 불가능 |
| AWS Chatbot | 관리형·무코드 | **Discord 미지원**(Slack/Teams만) | 대상 채널 미지원 |
| EventBridge API Destination | Lambda 없이 순수 IaC | Connection에 **더미 인증** 필요·InputTransformer JSON 템플릿이 까다로워 메시지 투박 | Lambda가 포맷 제어·견고성에서 우위, 비용 차 없음 |
| 이메일(SNS 직접 구독)만 유지 | 추가 0·가장 단순 | 팀 워크플로(Discord)와 분리 | Discord 요구 미충족 → 제거하고 일원화 |

## Consequences

- 긍정: 알람이 **Discord 채널로 즉시·가독성 있게**(색상 임베드) 전달. `discord_webhook_url` 한 줄로 on/off. 추가 비용 **≈ $0**(Lambda·SNS 프리티어, CloudWatch 알람 2개는 10개 무료 한도 내). 통보 채널이 Discord 하나로 단순화.
- 부정/트레이드오프: 변환 **Lambda 1개**가 늘어 운영 표면이 약간 커진다(로그·런타임 버전 관리). 웹훅 URL이 **Lambda 환경변수**로 보관돼 `aws lambda get-function-configuration` 에 노출(민감도 낮아 dev 수용, 필요 시 SSM SecureString으로 승격). Discord 장애 시 알람 유실(재시도는 SNS/Lambda 기본 정책).
- 후속 작업: 민감도가 올라가면 웹훅을 SSM SecureString으로 이전. 알람 종류가 늘면 임베드 포맷을 확장(현재 State/Region/Reason 필드).

## Validation

- `discord_webhook_url` 설정 후 `apply` → Lambda(`<prefix>-discord-notify`)·SNS lambda 구독 생성 확인.
- 강제 트리거: `aws cloudwatch set-alarm-state --alarm-name <prefix>-cpu-high --state-value ALARM --state-reason test` → Discord 채널에 빨강 임베드 수신. 이어 `--state-value OK` → 초록 수신.
- 미설정(`discord_webhook_url=""`) 시 Lambda·구독이 **생성되지 않음**(plan에 0개) 확인.
- Lambda 실패 시 CloudWatch Logs(`/aws/lambda/<prefix>-discord-notify`)에서 원인 확인.
