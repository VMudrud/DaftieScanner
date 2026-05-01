data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

resource "aws_security_group" "daftiescanner" {
  name        = "daftiescanner"
  description = "DaftieScanner EC2 — SSH inbound only"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "daftiescanner"
  }
}

resource "aws_iam_role" "daftiescanner_ec2" {
  name = "daftiescanner-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "daftiescanner_ec2" {
  name = "daftiescanner-ec2-policy"
  role = aws_iam_role.daftiescanner_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "DynamoDB"
        Effect = "Allow"
        Action = ["dynamodb:*"]
        Resource = [
          "arn:aws:dynamodb:${var.aws_region}:*:table/daftiescanner_seen",
          "arn:aws:dynamodb:${var.aws_region}:*:table/daftiescanner_cursor",
          "arn:aws:dynamodb:${var.aws_region}:*:table/daftiescanner_alerts"
        ]
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:log-group:/daftiescanner/*"
      },
      {
        Sid    = "CloudWatchMetrics"
        Effect = "Allow"
        Action = ["cloudwatch:PutMetricData"]
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "DaftieScanner"
          }
        }
        Resource = "*"
      },
      {
        Sid    = "SSMParameters"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:*:parameter/daftiescanner/*"
      },
      {
        Sid      = "SNSPublish"
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.alerts.arn
      }
    ]
  })
}

resource "aws_iam_instance_profile" "daftiescanner" {
  name = "daftiescanner-ec2"
  role = aws_iam_role.daftiescanner_ec2.name
}

# User data script — written to a local file so compose files can be embedded cleanly
locals {
  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    compose_yml     = file("${path.root}/../docker-compose.yml")
    compose_aws_yml = file("${path.root}/../docker-compose.aws.yml")
    systemd_unit    = file("${path.module}/systemd/daftiescanner.service")
  })
}

resource "aws_instance" "daftiescanner" {
  ami                         = data.aws_ami.al2023_arm64.id
  instance_type               = "t4g.nano"
  iam_instance_profile        = aws_iam_instance_profile.daftiescanner.name
  vpc_security_group_ids      = [aws_security_group.daftiescanner.id]
  associate_public_ip_address = true

  root_block_device {
    volume_type = "gp3"
    volume_size = 8
  }

  user_data = local.user_data

  tags = {
    Name = "daftiescanner"
  }
}

output "instance_id" {
  description = "EC2 instance ID — use as instance_id variable for lambda_ip_recycle"
  value       = aws_instance.daftiescanner.id
}

output "public_ip" {
  description = "EC2 public IP address (changes on stop/start — intentional for IP recycle)"
  value       = aws_instance.daftiescanner.public_ip
}
