# dev CI/CD — GitHub Actions(OIDC)가 assume할 dev 전용 배포 역할.
# 권한: ECR push(이미지) + dev EC2 SSM run-command(docker compose pull·up). prod의 iam 모듈과 독립이라
#   `terraform apply environments/dev` 만으로 CI/CD가 완결된다.
# GitHub OIDC provider는 계정 전역(URL당 1개) — bootstrap 이 단일 생성·소유한다. 여기선 data 로 조회만.

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

locals {
  oidc_provider_arn = data.aws_iam_openid_connect_provider.github.arn
  ecr_repo_arn      = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository}"
}

# 신뢰 정책 — 지정 repo의 지정 브랜치 push에서만 assume.
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

resource "aws_iam_role" "github_deploy" {
  name               = "${local.name_prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
  tags               = merge(local.common_tags, { Name = "${local.name_prefix}-github-deploy" })
}

# 권한 — ECR 인증·push(해당 repo만) + dev EC2에만 SSM run-command(최소권한).
data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # 인증 토큰은 리소스 한정 불가
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [local.ecr_repo_arn]
  }

  statement {
    sid     = "SsmDevDeploy"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:document/AWS-RunShellScript",
      "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${module.host.instance_id}",
    ]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${local.name_prefix}-github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
