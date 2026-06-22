# ECS Fargate — Kohere 백엔드(stateless, access 무상태 → 수평 확장). ALB 타깃 그룹에 등록.
# 태스크 정의/desired_count는 런타임에 CI/CD·오토스케일링이 바꾸므로 ignore_changes로 보호한다.

locals {
  container_name = var.container_name
  cluster_name   = "${var.name_prefix}-cluster"
  service_name   = "${var.name_prefix}-service"
}

resource "aws_ecs_cluster" "this" {
  name = local.cluster_name

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = merge(var.tags, { Name = local.cluster_name })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = merge(var.tags, { Name = "/ecs/${var.name_prefix}" })
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name_prefix
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  execution_role_arn       = var.task_execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = local.container_name
      image     = var.container_image
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = var.container_environment
      secrets     = var.container_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "app"
        }
      }

      stopTimeout = 30
    }
  ])

  tags = merge(var.tags, { Name = var.name_prefix })
}

resource "aws_ecs_service" "this" {
  name             = local.service_name
  cluster          = aws_ecs_cluster.this.id
  task_definition  = aws_ecs_task_definition.this.arn
  desired_count    = var.desired_count
  launch_type      = "FARGATE"
  platform_version = "LATEST"

  enable_execute_command = var.enable_execute_command

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = local.container_name
    container_port   = var.container_port
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 120
  propagate_tags                     = "SERVICE"

  lifecycle {
    # CI/CD가 새 태스크 정의를 등록·배포하고 오토스케일링이 desired_count를 조정하므로 TF가 되돌리지 않게 한다.
    ignore_changes = [task_definition, desired_count]
  }

  tags = merge(var.tags, { Name = local.service_name })
}

# --- CPU 타깃 추적 오토스케일링 ---
resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = var.autoscaling_max
  min_capacity       = var.autoscaling_min
  resource_id        = "service/${local.cluster_name}/${local.service_name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"

  depends_on = [aws_ecs_service.this]
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name_prefix}-cpu-target"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = var.cpu_target
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
