provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

# CloudFront 인증서(ACM)는 반드시 us-east-1에 있어야 한다 — CDN 커스텀 도메인용 별도 provider.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}
