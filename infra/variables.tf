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
  description = "CIDR for SSH inbound rule — your home/VPN IP, e.g. 203.0.113.7/32. No default: must be set explicitly (via terraform.tfvars or -var) to avoid exposing SSH to 0.0.0.0/0."
  type        = string

  validation {
    condition     = var.ssh_cidr != "0.0.0.0/0"
    error_message = "ssh_cidr must not be 0.0.0.0/0 — restrict SSH to a specific address range."
  }
}

variable "ssm_prefix" {
  description = "SSM Parameter Store path prefix for application config"
  type        = string
  default     = "/daftiescanner/prod"
}
