# dev 데이터 EBS — mysql/mongo 영속(Redis는 인메모리). 암호화, 인스턴스 교체에도 잔존.
resource "aws_ebs_volume" "data" {
  availability_zone = var.availability_zone
  size              = var.size
  type              = "gp3"
  encrypted         = true
  tags              = merge(var.tags, { Name = "${var.name_prefix}-data" })

  lifecycle {
    prevent_destroy = true
  }
}
