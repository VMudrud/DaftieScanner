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

variable "ssh_cidr" {
  description = "CIDR for SSH inbound rule — restrict in production"
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssm_prefix" {
  description = "SSM Parameter Store path prefix for application config"
  type        = string
  default     = "/daftiescanner/prod"
}
