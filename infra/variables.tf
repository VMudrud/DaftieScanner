variable "aws_region" {
  description = "AWS region where resources are deployed"
  type        = string
  default     = "eu-west-1"
}

variable "instance_id" {
  description = "EC2 instance ID to recycle on block_detected alarm (populate after first apply)"
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Email address to receive CloudWatch alarm notifications"
  type        = string
}

variable "ssm_prefix" {
  description = "SSM Parameter Store path prefix for application config"
  type        = string
  default     = "/daftiescanner/prod"
}
