output "state_bucket_name" {
  description = "원격 상태 S3 버킷 이름 → environments/prod/backend.tf 의 bucket"
  value       = aws_s3_bucket.state.bucket
}

output "lock_table_name" {
  description = "상태 잠금 DynamoDB 테이블 이름 → environments/prod/backend.tf 의 dynamodb_table"
  value       = aws_dynamodb_table.lock.name
}

output "region" {
  description = "리전"
  value       = var.aws_region
}
