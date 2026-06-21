terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# bootstrap는 원격 백엔드(S3)를 만드는 단계 → 최초 apply는 로컬 state. 이후 backend.tf로 자기 state를 S3로 이전한다.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
      Component   = "tf-state-backend"
    }
  }
}
