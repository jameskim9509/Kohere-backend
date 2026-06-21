terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# bootstrap는 원격 백엔드를 만드는 단계라 로컬 state를 쓴다(여기에는 backend 블록을 두지 않는다).
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
