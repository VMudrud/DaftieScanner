# DaftieScanner — Deployment Guide

End-to-end guide for deploying DaftieScanner to AWS.

## Architecture

| Component | What it is |
|-----------|------------|
| EC2 `t4g.micro` (ARM64, Amazon Linux 2023) | Runs the app in Docker via a `systemd` unit |
| 3× DynamoDB tables | `daftiescanner_seen`, `_cursor`, `_alerts` (PAY_PER_REQUEST) |
| SSM Parameter Store (`/daftiescanner/prod/*`) | Runtime config + secrets, loaded at container start |
| CloudWatch + SNS + Lambda | Block-detection alarm → SNS → Lambda stops/starts the instance to recycle the public IP |
| IAM instance role | Least-privilege access to the above + SSM Session Manager |

**Shell access is via SSM Session Manager** — there is no inbound SSH (the security group has no ingress rules). **The Docker image is built on the instance** (it's ARM64; there is no ECR registry in this setup).

Notes on the Terraform setup:
- **No `provider`/`backend` block** → state is **local** (`infra/terraform.tfstate`, gitignored). The AWS region comes from your environment (`aws configure` / `AWS_REGION`), not a provider block. Default region: `eu-west-1`.
- Provider versions are pinned in `infra/.terraform.lock.hcl` (committed).

## Prerequisites (your machine)

- **Terraform** ≥ 1.5, **AWS CLI v2**, and the **Session Manager plugin** for the AWS CLI ([install guide](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html)).
- AWS credentials configured for the target account:
  ```powershell
  aws configure                 # set key, secret, region = eu-west-1
  aws sts get-caller-identity   # confirm authentication
  ```

## Phase 1 — Security gate

Rotate the **Telegram bot token** in @BotFather (`/revoke` → select your bot → copy the new token). Keep it for Phase 3. (The previous token was exposed in logs during development.)

## Phase 2 — Provision infrastructure

Create `infra/terraform.tfvars` (gitignored):

```hcl
alert_email = "you@example.com"
```

From `infra/`:

```powershell
terraform init
terraform plan      # review: 3 DynamoDB tables, EC2, IAM role + SSM policy, SG (egress only), ~13 SSM params, SNS, CloudWatch alarm, Lambda
terraform apply
```

This provisions everything but does **not** start the app. The instance boots, installs Docker + the Compose plugin, writes `docker-compose.yml` + `docker-compose.aws.yml` to `/opt/daftiescanner`, and **enables (not starts)** the `daftiescanner` systemd unit.

Then do a second apply to wire the IP-recycle Lambda to the instance ID:

```powershell
terraform apply -var="instance_id=$(terraform output -raw instance_id)"
```

Record the outputs: `terraform output instance_id` and `terraform output public_ip`.

## Phase 3 — Populate SSM parameters

Terraform-managed params (`infra/ssm.tf`) are created with value `REPLACE_ME`; `lifecycle { ignore_changes = [value] }` means Terraform won't overwrite values you set. The container's `entrypoint.sh` loads **everything** under `/daftiescanner/prod` recursively **with decryption**, so any parameter you add manually is also picked up.

**Set the managed placeholders.** Use `--type String` — Terraform creates these as `String` and SSM won't let `--overwrite` change a parameter's type. (Making `TENANT_1_EMAIL` a `SecureString` is deferred hardening; it would require changing `ssm.tf`, not just the CLI.)

```powershell
aws ssm put-parameter --name /daftiescanner/prod/TENANTS_ACTIVE            --value "1"                                          --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_ENABLED          --value "true"                                       --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_EMAIL            --value "you@example.com"                      --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_SECTION          --value "residential-to-rent"                        --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_RENTAL_PRICE_MIN --value "1200"                                       --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_RENTAL_PRICE_MAX --value "2300"                                       --type String --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_STORED_SHAPE_IDS --value "65,66,67,68,70,71,72,73,74,75,77,79"        --type String --overwrite
# num-beds min/max: not created in SSM on purpose (blank = no filter; SSM rejects empty values).
```

**Add the secrets / params not managed by Terraform** (token as `SecureString`):

```powershell
aws ssm put-parameter --name /daftiescanner/prod/DAFT_TELEGRAM_TOKEN          --value "<NEW_ROTATED_TOKEN>" --type SecureString --overwrite
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_NOTIFIERS           --value "logging,telegram"    --type String       --overwrite
aws ssm put-parameter --name /daftiescanner/prod/DAFT_TELEGRAM_ADMIN_CONTACT  --value "@YourHandle"         --type String       --overwrite
```

> **`.env.aws.example` is the source of truth for the full key list** (notifiers, BER ratings, poll tuning, alert config). Cross-check it. `DAFT_DYNAMO_ENDPOINT` and the CloudWatch metrics flag are already set in `docker-compose.aws.yml`, so they don't need SSM entries.

## Phase 4 — Build the Docker image on the instance

The instance is ARM64, so build there (no cross-compilation, no registry needed). Connect via Session Manager:

```powershell
aws ssm start-session --target (terraform output -raw instance_id)
```

> The SSM agent needs ~1–2 min after first boot to register. If `start-session` reports the target isn't connected, wait and retry; confirm with `aws ssm describe-instance-information`.

In the session (you start as `ssm-user`):

```bash
sudo dnf install -y git
sudo git clone <your-repo-url> /opt/src
cd /opt/src
sudo docker build -t daftiescanner:latest .
```

## Phase 5 — Start the application

Still on the instance:

```bash
sudo systemctl start daftiescanner
sudo systemctl status daftiescanner
sudo docker compose -f /opt/daftiescanner/docker-compose.yml \
                    -f /opt/daftiescanner/docker-compose.aws.yml logs -f
```

On start, `entrypoint.sh` fetches SSM params via the instance-profile credentials (IMDSv2, reachable from the container thanks to `http_put_response_hop_limit = 2`), exports them, and launches the JVM (heap sized from container memory via `-XX:MaxRAMPercentage`).

## Phase 6 — Verify end-to-end

1. **Logs**: "SSM config loaded", Spring startup, "Scheduled Telegram update poller". No `/bot<token>/` appears (redaction is in place).
2. **Telegram**: send your bot `/subscribe you@example.com` → you get a confirmation.
3. **Listings**: within a poll cycle (~60s + jitter) you receive listing messages (one per listing, table + Copy-link button).
4. **CloudWatch**: the `/daftiescanner/app` log group fills (awslogs driver) and the `DaftieScanner` metric namespace shows data.
5. **DynamoDB**: `daftiescanner_seen` / `_cursor` start getting items.

## Phase 7 — Operations

- **Redeploy a config-only change** (e.g. `TENANT_1_STORED_SHAPE_IDS`, price bounds, notifiers): the app reads runtime config from **SSM**, not from `.env.aws` (that file is local-Docker only). Update the param, then restart so `entrypoint.sh` reloads it — no image rebuild. From your machine (region `eu-west-1`):

  ```powershell
  # 1. Push the new value (use --type String; SSM won't change an existing param's type)
  aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_STORED_SHAPE_IDS `
      --value "65,66,67,68,70,71,72,73,74,75,77,79,2067" --type String --overwrite

  # 2. Restart non-interactively via Run Command (no SSM shell needed)
  $cmd = aws ssm send-command --instance-ids i-030fb9095316345c5 `
      --document-name AWS-RunShellScript `
      --parameters 'commands=["systemctl restart daftiescanner"]' `
      --query Command.CommandId --output text

  # 3. Verify config reloaded + app started
  aws ssm send-command --instance-ids i-030fb9095316345c5 --document-name AWS-RunShellScript `
      --parameters 'commands=["sleep 40","docker logs --tail 30 daftiescanner-app-1 2>&1 | grep -iE \"SSM config loaded|enabled tenant|Started Daftie\""]' `
      --query Command.CommandId --output text
  # then: aws ssm get-command-invocation --command-id <id> --instance-id i-030fb9095316345c5 --query StandardOutputContent --output text
  ```

  Notes: from Git Bash, prefix slash-path args with `MSYS_NO_PATHCONV=1` to stop MSYS mangling the SSM name. The systemd `status` output contains a `●` glyph that the AWS CLI on Windows can't encode — read `get-command-invocation` from PowerShell with `[Console]::OutputEncoding=[Text.Encoding]::UTF8` if needed. Instance ID: `terraform output -raw instance_id`.
- **Redeploy after a code change**: in an SSM session, `cd /opt/src && sudo git pull && sudo docker build -t daftiescanner:latest .`, then `sudo systemctl restart daftiescanner`. (Or run the same `git pull`/`build`/`restart` non-interactively via the Run Command pattern above.)
- **IP recycle**: on a `block_detected` CloudWatch alarm, SNS triggers the Lambda to stop/start the instance, rotating the public IP (works because no Elastic IP is attached). **The public IP changes on stop/start** — re-fetch with `terraform output public_ip` or the console. SSM access is unaffected (it doesn't depend on the IP).
- **Pause to save cost**: `sudo systemctl stop daftiescanner`, or stop the instance.
- **Tear down**: `terraform destroy` (from `infra/`). DynamoDB tables are deleted — export first if you want to keep state.

## Optional hardening (deferred from the security review)

Not blocking, but easy to fold in before/after first apply (see the security review for details):
- Container runs as root — add a non-root `USER` to the `Dockerfile`.
- EBS root volume unencrypted — add `encrypted = true` to `root_block_device` in `ec2.tf`.
- IAM `dynamodb:*` — narrow to the specific actions the app uses (`ec2.tf`).
- The managed SSM params use `type = "String"` — switch any sensitive ones to `SecureString`.
