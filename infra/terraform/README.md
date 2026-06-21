# Kohere 인프라 (Terraform · AWS)

[system-overview §1-3-2 클라우드 배포 아키텍처](../../docs/architecture/system-overview.md#1-3-2-클라우드-배포-아키텍처-m7-이전배포-aws)를 Terraform으로 구현한 것이다. 리전은 **ap-northeast-2(서울)**, 환경은 **prod 단일**.

```
모바일 앱 ──HTTPS──▶ ALB ──▶ ECS Fargate(Spring Boot · :8080)
                                  │  ├─ JDBC ───▶ RDS for MySQL 8.0    (auth·user)
                                  │  ├─ mongodb ▶ DocumentDB           (listing·diagnosis)
                                  │  └─ redis ──▶ ElastiCache(Redis)   (refresh 토큰)
                                  └─ 시크릿 ────▶ Secrets Manager
앱 이미지: GitHub Actions ──OIDC──▶ ECR ──▶ Fargate
매물 이미지: S3 ──OAC──▶ CloudFront (클라이언트 직접 로드)
```

## 결정 사항

| 항목 | 선택 | 비고 |
| --- | --- | --- |
| MongoDB | **Amazon DocumentDB** | AWS 네이티브(단일 provider, VPC 내). `SPRING_DATA_MONGODB_URI`는 Mongo 드라이버 배선(추후)에 대비해 미리 주입 |
| 환경 | **prod 단일** | 재사용 모듈 + `environments/prod`. dev/stage는 동일 모듈 복제로 추가 가능 |
| 도메인·TLS | **옵셔널 변수** | `domain_name`+`route53_zone_id` 제공 시 ACM(DNS 검증)·HTTPS·Route53 alias 생성, 아니면 ALB는 HTTP(80)만 |
| 컴퓨트 | ECS Fargate + ALB | access 무상태 → 타깃 추적 오토스케일링(CPU) |
| 상태 | S3 + DynamoDB 잠금 | `bootstrap/` 에서 1회 생성 |

## 디렉터리

```
infra/terraform/
├── bootstrap/                 # 원격 상태 백엔드(S3+DynamoDB). 최초 1회.
├── modules/                   # 재사용 모듈
│   ├── network/               # VPC, 3-tier 서브넷, NAT, VPC 엔드포인트
│   ├── security/              # 보안 그룹(alb·app·rds·docdb·redis)
│   ├── ecr/                   # 앱 이미지 레지스트리
│   ├── acm/                   # ALB용 TLS 인증서(옵셔널)
│   ├── secrets/               # 앱 시크릿(JWT·pepper·OIDC·SMTP)
│   ├── iam/                   # ECS 역할 + GitHub OIDC 배포 역할
│   ├── rds/                   # RDS for MySQL 8.0
│   ├── documentdb/            # Amazon DocumentDB
│   ├── elasticache/           # ElastiCache Redis(복제 그룹)
│   ├── alb/                   # Application Load Balancer
│   ├── ecs/                   # Fargate 클러스터·태스크·서비스·오토스케일링
│   ├── s3-cloudfront/         # 매물 이미지(S3 + CloudFront OAC)
│   └── monitoring/            # SNS + CloudWatch 알람
└── environments/prod/         # 모듈 배선(루트)
```

## 사전 준비

- Terraform >= 1.6, AWS CLI, 배포 권한이 있는 AWS 자격증명(`aws configure` 또는 SSO).
- 로컬에는 Terraform/AWS CLI가 설치돼 있지 않을 수 있다 — 먼저 설치한다.

## 적용 순서

```bash
# 0) 원격 상태 백엔드 생성(최초 1회)
cd infra/terraform/bootstrap
terraform init && terraform apply
#   → 출력된 state_bucket_name / lock_table_name 을 environments/prod/backend.tf 에 채운다.

# 1) prod 인프라
cd ../environments/prod
cp terraform.tfvars.example terraform.tfvars   # 값 채우기(도메인·OIDC·SMTP 등)
terraform init -reconfigure                    # 원격 백엔드 연결
terraform plan
terraform apply
```

> ⚠️ **첫 apply 시점에는 ECR이 비어 있다.** ECS 서비스는 이미지를 풀할 때까지 태스크가 헬시해지지 않는다.
> apply 직후(또는 직전) GitHub Actions/수동으로 이미지를 한 번 push하면 서비스가 정상화된다(아래 CI/CD).

## CI/CD (GitHub Actions → ECR → Fargate)

`module.iam`이 GitHub OIDC provider와 배포 역할을 만든다. 배포 역할은 **`<github_org>/<github_repo>` 의 `<github_deploy_branch>` 브랜치**에서만 assume할 수 있고, 권한은 해당 ECR 리포지토리 push + 해당 ECS 서비스 업데이트로 한정된다.

1. `terraform output github_actions_role_arn` 값을 GitHub 리포지토리 **Variables**의 `AWS_DEPLOY_ROLE_ARN` 에 설정.
2. 동봉한 [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) 이 `main` push 시 이미지 빌드→ECR push→`aws ecs update-service --force-new-deployment` 를 수행한다(`AWS_DEPLOY_ROLE_ARN` 이 설정돼 있을 때만 동작).

## 운영 전 반드시 채울 값 (앱 fail-fast)

`application-prod.yml` 은 누락 시 기동 실패한다. 다음 시크릿을 `terraform.tfvars` 로 주입하거나, apply 후 `app_secret_arn` 시크릿을 콘솔에서 직접 편집한다:

- `google_client_id`, `apple_client_id` — OIDC audience
- `smtp_host`/`smtp_port`/`smtp_username`/`smtp_password` — 운영 SMTP(예: Amazon SES SMTP)

`JWT_SECRET`·`REFRESH_PEPPER`·`EMAIL_PEPPER` 는 Terraform이 자동 생성한다.

## 후속 작업 / 알아둘 점

- **DocumentDB TLS**: `docdb_tls=enabled`(기본)면 연결 URI에 `tls=true` 가 붙는다. Mongo 드라이버 배선 시 앱 이미지에 Amazon DocumentDB CA 번들(`global-bundle.pem`)을 포함해야 한다. 검증 단계에서 막히면 일시적으로 `docdb_tls=disabled` 로 둘 수 있다(VPC 내부 통신).
- **RDS TLS**: JDBC URL은 `serverTimezone=UTC`(로컬과 동일). Connector/J 8.x 기본 SSL(PREFERRED). 엄격 검증이 필요하면 `sslMode=VERIFY_CA` + RDS CA 임포트.
- **Redis 전송 암호화**: 기본 off(앱이 host/port만 사용). 켜려면 `redis_transit_encryption=true` + Spring SSL/auth token 설정 필요.
- **NAT**: 기본 `single_nat_gateway=true`(비용 절감). HA가 필요하면 `false`.
- **삭제 보호**: RDS·DocumentDB 기본 `deletion_protection=true`. `terraform destroy` 전에 false로 변경 필요.
- **상태에 시크릿 포함**: 생성된 비밀번호/키가 state에 들어간다 — 원격 state(S3)는 암호화+버전관리+TLS 강제로 보호된다.
- **CloudFront 커스텀 도메인**: 기본은 CloudFront 기본 도메인. 커스텀 도메인을 쓰려면 us-east-1 ACM 인증서가 별도로 필요(현재 범위 밖).
