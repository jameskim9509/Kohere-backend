# 프론트엔드(임대인 웹) 배포 경로 — 프론트 레포의 GitHub Actions가 assume할 전용 역할과, 그 역할이
# 호스트에서 실행할 수 있는 단 하나의 명령(SSM Document)을 정의한다(#232).
#
# 백엔드 역할(cicd.tf)에 sub 를 한 줄 더 얹지 않고 역할을 나눈 이유: 한 역할에 몰면 프론트 레포가
#   ECR push 와 AWS-RunShellScript(= 호스트 root 임의 실행)까지 함께 얻는다. 레포 하나가 털렸을 때
#   번지는 범위를 나눠 두는 것이 요점이다.
# OIDC provider 는 계정당 URL 1개 싱글톤이라 새로 만들지 않는다 — cicd.tf 가 data 로 조회해 둔 것을 쓴다.

# 이 역할이 호스트에서 할 수 있는 일은 이 문서 하나뿐이다. AWS-RunShellScript 를 주면 임의 셸 실행이 되므로
# 실행 대상을 deploy-web.sh 로 고정하고, sha 는 allowedPattern 으로 16진수만 받아 명령 주입을 막는다.
resource "aws_ssm_document" "deploy_web" {
  name            = "${local.name_prefix}-deploy-web"
  document_type   = "Command"
  document_format = "YAML"

  content = yamlencode({
    schemaVersion = "2.2"
    description   = "dev 호스트에 프론트엔드 릴리스를 적용한다(배포·롤백 공통)"
    parameters = {
      sha = {
        type           = "String"
        description    = "적용할 릴리스 커밋 SHA"
        allowedPattern = "^[0-9a-f]{7,40}$"
      }
    }
    mainSteps = [{
      action = "aws:runShellScript"
      name   = "deployWeb"
      inputs = {
        runCommand = ["bash /opt/kohere/deploy-web.sh '{{ sha }}'"]
      }
    }]
  })

  tags = merge(local.common_tags, { Name = "${local.name_prefix}-deploy-web" })
}

# 신뢰 정책 — 프론트 레포의 지정 브랜치 push에서만 assume. 조건이 없으면 github.com 전체가 통과한다
#   (발급자가 GitHub Actions 전역에 하나뿐이고, aud 는 요청 시 아무나 지정할 수 있다).
data "aws_iam_policy_document" "github_web_assume" {
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
      values   = ["repo:${var.github_org}/${var.github_web_repo}:ref:refs/heads/${var.github_web_deploy_branch}"]
    }
  }
}

resource "aws_iam_role" "github_web_deploy" {
  name               = "${local.name_prefix}-github-deploy-web"
  assume_role_policy = data.aws_iam_policy_document.github_web_assume.json
  tags               = merge(local.common_tags, { Name = "${local.name_prefix}-github-deploy-web" })
}

data "aws_iam_policy_document" "github_web_deploy" {
  # 업로드만 준다. GetObject 는 필요 없고(업로드 방향 sync 는 목록만 비교한다), DeleteObject 도 필요 없다
  #   (매번 새 SHA 프리픽스에 올리므로 지울 대상이 없다). 덕분에 CI가 과거 릴리스를 지울 수 없고,
  #   릴리스 불변성이 관례가 아니라 IAM으로 강제된다. 보관 기간은 라이프사이클 규칙만 소유한다.
  statement {
    sid       = "UploadRelease"
    actions   = ["s3:PutObject"]
    resources = ["${module.web.bucket_arn}/releases/*"]
  }

  # ListBucket 은 객체가 아니라 버킷에 걸리는 액션이라 문장을 나눈다 — 객체 ARN에 붙이면 sync 가 AccessDenied 난다.
  statement {
    sid       = "ListForSync"
    actions   = ["s3:ListBucket"]
    resources = [module.web.bucket_arn]
  }

  # current.txt 쓰기는 주지 않는다. 포인터는 호스트가 링크 교체에 성공한 뒤에만 옮긴다 —
  #   CI가 SSM 결과를 보기도 전에 "이게 라이브다"라고 선언하는 상태를 구조적으로 막는다.
  statement {
    sid     = "RunDeployWeb"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_ssm_document.deploy_web.arn,
      "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${module.host.instance_id}",
    ]
  }

  # 배포 결과를 끝까지 지켜보기 위한 폴링. send-command 는 큐잉만 확인하고 0을 리턴하므로,
  #   이 권한이 없으면 호스트에서 무엇이 실패했든 워크플로가 초록불이 된다.
  #   두 액션 모두 리소스 한정을 지원하지 않는다.
  statement {
    sid       = "PollCommandResult"
    actions   = ["ssm:ListCommandInvocations", "ssm:GetCommandInvocation"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_web_deploy" {
  name   = "${local.name_prefix}-github-deploy-web"
  role   = aws_iam_role.github_web_deploy.name
  policy = data.aws_iam_policy_document.github_web_deploy.json
}
