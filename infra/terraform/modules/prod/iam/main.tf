# IAM — ECS 태스크 실행 역할 / 태스크 역할 / GitHub Actions OIDC 배포 역할(+ OIDC provider).
# 최소권한 원칙: 실행 역할은 지정 시크릿만 읽고, 배포 역할은 지정 ECR/ECS 리소스에만 접근.

locals {
  ecs_service_arn = "arn:aws:ecs:${var.region}:${var.account_id}:service/${var.name_prefix}-cluster/${var.name_prefix}-service"
}

# ===== ECS 태스크 실행 역할 (이미지 풀·로그·시크릿 주입) =====
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${var.name_prefix}-ecs-task-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-ecs-task-exec" })
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# SSM Parameter Store SecureString 읽기 + aws/ssm 키 복호화(Secrets Manager 미사용, ADR-0023).
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    sid       = "ReadParams"
    actions   = ["ssm:GetParameters"]
    resources = var.secret_arns # SSM 파라미터 ARN 목록(앱·rds·docdb)
  }

  statement {
    sid       = "DecryptParams"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "${var.name_prefix}-ecs-task-exec-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

# ===== ECS 태스크 역할 (앱 런타임) — ECS Exec(디버깅) + 콘텐츠 이미지 S3 업로드 =====
resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-ecs-task" })
}

data "aws_iam_policy_document" "task_exec_command" {
  statement {
    sid = "ECSExec"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }

  # 콘텐츠 이미지 버킷 읽기/쓰기(앱이 태스크 역할로 업로드) — images_bucket_arn 제공 시에만.
  dynamic "statement" {
    for_each = var.images_bucket_arn != "" ? [1] : []
    content {
      sid       = "ImagesBucketRW"
      actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
      resources = [var.images_bucket_arn, "${var.images_bucket_arn}/*"]
    }
  }
}

resource "aws_iam_role_policy" "task_exec_command" {
  name   = "${var.name_prefix}-ecs-exec"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_exec_command.json
}

# ===== GitHub Actions OIDC provider — bootstrap 이 계정당 1개 생성·소유. 여기선 data 로 조회만. =====
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

locals {
  oidc_provider_arn = data.aws_iam_openid_connect_provider.github.arn
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${var.github_repo}:ref:refs/heads/${var.github_deploy_branch}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${var.name_prefix}-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-github-actions-deploy" })
}

data "aws_iam_policy_document" "github_deploy" {
  # ECR 로그인 토큰은 리소스 한정 불가.
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [var.ecr_repository_arn]
  }

  # 태스크 정의 등록/조회는 리소스 한정 불가.
  statement {
    sid = "EcsRegisterTaskDef"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcsUpdateService"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
    ]
    resources = [local.ecs_service_arn]
  }

  statement {
    sid       = "PassEcsRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.task.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # dev 배포 — SSM run-command로 dev EC2에서 docker compose pull·up (ECS 없는 dev용). prod·dev 공용 배포 역할.
  statement {
    sid     = "SsmDevDeploy"
    actions = ["ssm:SendCommand"]
    resources = [
      # AWS-RunShellScript는 AWS 소유 퍼블릭 문서 — ARN에 계정 ID가 없다(:: 빈 계정). 계정 ID를 박으면 매칭 실패로 SendCommand 거부됨.
      "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
      "arn:aws:ec2:${var.region}:${var.account_id}:instance/*",
    ]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${var.name_prefix}-github-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
