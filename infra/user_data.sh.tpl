#!/bin/bash
set -e

# Install Docker
dnf install -y docker
systemctl enable docker
systemctl start docker

# Install Docker Compose plugin (ARM64)
ARCH=aarch64
COMPOSE_VERSION=$(curl -sf https://api.github.com/repos/docker/compose/releases/latest \
  | grep '"tag_name"' | cut -d'"' -f4)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/download/$${COMPOSE_VERSION}/docker-compose-linux-$${ARCH}" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Create application directory
mkdir -p /opt/daftiescanner

# Write compose files (embedded by Terraform at plan time)
cat > /opt/daftiescanner/docker-compose.yml << 'EOF_COMPOSE'
${compose_yml}
EOF_COMPOSE

cat > /opt/daftiescanner/docker-compose.aws.yml << 'EOF_COMPOSE_AWS'
${compose_aws_yml}
EOF_COMPOSE_AWS

# Install systemd unit
cat > /etc/systemd/system/daftiescanner.service << 'EOF_UNIT'
${systemd_unit}
EOF_UNIT

systemctl daemon-reload
systemctl enable daftiescanner
