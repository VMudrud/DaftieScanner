# DaftieScanner — Infrastructure

Terraform configuration for the AWS deployment (EC2 + DynamoDB + SSM + CloudWatch).

> **For the full deployment procedure, see [`../DEPLOYMENT.md`](../DEPLOYMENT.md).**
> This README is an infra reference only — what's defined here and how to operate Terraform.

## Files

| File | Defines |
|------|---------|
| `ec2.tf` | EC2 `t4g.micro` (AL2023 ARM64), security group (**egress only — no inbound**), IAM role + instance profile, `AmazonSSMManagedInstanceCore` attachment, AMI lookup |
| `dynamo.tf` | DynamoDB tables `daftiescanner_seen`, `_cursor`, `_alerts` (PAY_PER_REQUEST, TTL) |
| `ssm.tf` | SSM parameters under `/daftiescanner/prod/*` (created as `REPLACE_ME`; `ignore_changes` keeps your values) |
| `cloudwatch.tf` | Block-detection alarm + SNS topic |
| `lambda_ip_recycle.tf`, `lambda/ip_recycle.py` | Lambda that stops/starts the instance to recycle the public IP on alarm |
| `user_data.sh.tpl` | Instance bootstrap: installs Docker + Compose, writes compose files, installs/enables the systemd unit |
| `systemd/daftiescanner.service` | `systemd` unit that runs `docker compose ... up -d` |
| `variables.tf` | Input variables (below) |

## Variables

| Variable | Default | Notes |
|----------|---------|-------|
| `aws_region` | `eu-west-1` | Used in IAM ARNs. Provider region comes from your AWS CLI/env config. |
| `alert_email` | _(required)_ | Recipient for CloudWatch alarm notifications. |
| `instance_id` | `""` | Populate after first apply to wire the IP-recycle Lambda (two-pass apply). |
| `ssm_prefix` | `/daftiescanner/prod` | SSM path prefix the app loads config from. |

## Access & state

- **Shell access is via SSM Session Manager** — `aws ssm start-session --target $(terraform output -raw instance_id)`. There is no inbound SSH.
- **State is local** (`terraform.tfstate` in this dir, gitignored) — no remote backend configured. Provider versions are pinned in `.terraform.lock.hcl` (committed).

## Quick reference

```bash
terraform init
terraform apply                                                     # first pass
terraform apply -var="instance_id=$(terraform output -raw instance_id)"   # wire IP-recycle Lambda
```

See [`../DEPLOYMENT.md`](../DEPLOYMENT.md) for prerequisites, SSM parameter population, building the image, starting the app, and verification.
