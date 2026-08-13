variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "bucket_name" {
  description = "콘텐츠 이미지 버킷 이름 — 필수(S3는 전역 유일)"
  type        = string
}

variable "price_class" {
  description = "CloudFront 가격 등급(PriceClass_200은 아시아 포함)"
  type        = string
  default     = "PriceClass_200"
}

# 기본값 false — 앱이 업로드 실패 시 보상 삭제로 객체를 되돌리는데(ADR-0041), 버전 관리가 켜져 있으면
# DeleteObject가 삭제 마커만 남기고 이전 버전이 그대로 과금돼 "지웠다"가 성립하지 않는다.
# 이미지는 덮어쓰지 않고 되돌릴 일도 없어 버전을 보관할 이유가 없다.
variable "enable_versioning" {
  description = "S3 버전 관리 활성화 — 켜면 앱의 보상 삭제가 객체를 실제로 지우지 못한다"
  type        = bool
  default     = false
}

variable "abort_incomplete_multipart_days" {
  description = "완료되지 않은 멀티파트 업로드 조각을 정리하기까지의 일수"
  type        = number
  default     = 7
}

# ----- 커스텀 도메인(별칭 + ACM) — 필수. HTTPS·커스텀 도메인 강제 -----
variable "domain_aliases" {
  description = "CloudFront 별칭 도메인 목록(예: [\"cdn.kohere.app\"]) — 필수"
  type        = list(string)
}

variable "acm_certificate_arn" {
  description = "별칭에 쓸 ACM 인증서 ARN(**반드시 us-east-1**) — 필수"
  type        = string
}

variable "route53_zone_id" {
  description = "별칭 A/AAAA(alias) 레코드를 만들 Route53 호스팅 영역 ID — 필수"
  type        = string
}
