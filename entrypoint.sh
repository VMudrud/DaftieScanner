#!/bin/sh
set -e

# If SSM_PARAM_PREFIX is set, fetch all parameters under that path and export them as
# env vars before starting the JVM.
#
# Parameter path /daftiescanner/prod/TENANT_1_EMAIL
#   strip prefix  → /TENANT_1_EMAIL
#   strip leading slash → TENANT_1_EMAIL
#   tr '/' '_', uppercase → TENANT_1_EMAIL (no-op here; handles nested paths)
if [ -n "${SSM_PARAM_PREFIX}" ]; then
  echo "Fetching config from SSM prefix: ${SSM_PARAM_PREFIX}"

  aws ssm get-parameters-by-path \
    --path "${SSM_PARAM_PREFIX}" \
    --with-decryption \
    --recursive \
    --query "Parameters[*].[Name,Value]" \
    --output text \
  > /tmp/ssm_params.txt

  while IFS=$(printf '\t') read -r name value; do
    short="${name#${SSM_PARAM_PREFIX}}"
    short="${short#/}"
    env_name=$(printf '%s' "$short" | tr '/' '_' | tr '[:lower:]' '[:upper:]')
    export "${env_name}=${value}"
    echo "  Loaded: ${env_name}"
  done < /tmp/ssm_params.txt

  rm -f /tmp/ssm_params.txt
  echo "SSM config loaded."
fi

# Launch the JVM — tuned for t4g.nano (512 MB RAM, ARM64)
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -jar app.jar
