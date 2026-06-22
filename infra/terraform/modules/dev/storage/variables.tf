variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "tags" {
  description = "공통 태그"
  type        = map(string)
  default     = {}
}

variable "availability_zone" {
  description = "볼륨 가용영역(호스트 subnet과 동일해야 attach 가능)"
  type        = string
}

variable "size" {
  description = "데이터 EBS 크기(GB)"
  type        = number
  default     = 20
}
