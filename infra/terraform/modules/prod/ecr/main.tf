# 앱 컨테이너 이미지 레지스트리. 로컬·클라우드 공통 동일 이미지를 여기에 push.

resource "aws_ecr_repository" "this" {
  name                 = var.repo_name
  image_tag_mutability = var.image_tag_mutability
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = var.scan_on_push
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(var.tags, { Name = var.repo_name })
}

# 최신 N개만 보관 — 누적 이미지 비용 방지.
resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last ${var.image_retention_count} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.image_retention_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
