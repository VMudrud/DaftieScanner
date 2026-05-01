# DaftieScanner — Infrastructure

Terraform configuration for the EC2 + DynamoDB + SSM + CloudWatch deployment.

## Prerequisites

- Terraform >= 1.5
- AWS credentials configured (`aws configure` or env vars `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`)
- Docker with buildx installed (for multi-platform builds)

## Building the Docker image

The EC2 instance pulls the image by name (`daftiescanner:latest`). Since there is no ECR in this setup, choose one of:

**Option A — build directly on EC2 (simplest)**

```bash
# SSH to the instance after terraform apply
ssh ec2-user@<public_ip>
git clone <repo> /opt/src
cd /opt/src
docker build --platform linux/arm64 -t daftiescanner:latest .
```

**Option B — build locally and scp the image**

```bash
docker buildx build --platform linux/arm64 -t daftiescanner:latest --load .
docker save daftiescanner:latest | gzip > daftiescanner.tar.gz
scp daftiescanner.tar.gz ec2-user@<public_ip>:/opt/daftiescanner/
ssh ec2-user@<public_ip> "docker load < /opt/daftiescanner/daftiescanner.tar.gz"
```

## Deploy infrastructure

```bash
cd infra
terraform init
terraform apply -var="alert_email=you@example.com"
```

The `instance_id` variable defaults to `""` on first apply. After apply, note the `instance_id` output and re-apply to wire the Lambda IP recycle:

```bash
terraform apply \
  -var="alert_email=you@example.com" \
  -var="instance_id=$(terraform output -raw instance_id)"
```

## Fill in SSM parameters

All parameters are created with value `REPLACE_ME`. Set real values via console or CLI:

```bash
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_EMAIL \
  --value "your@email.com" --type String --overwrite
# repeat for each parameter
```

Terraform will not overwrite values you have set (`lifecycle { ignore_changes = [value] }`).

## Start the application

```bash
ssh ec2-user@<public_ip>
# Ensure image is loaded (see Building section above)
sudo systemctl start daftiescanner
sudo systemctl status daftiescanner
# Follow logs
sudo docker compose -f /opt/daftiescanner/docker-compose.yml \
  -f /opt/daftiescanner/docker-compose.aws.yml logs -f
```
