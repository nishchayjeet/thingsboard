#!/usr/bin/env bash
#
# dev-run.sh — run the ThingsBoard app on this Mac for local development
# while pointing at services hosted on the remote test server.
#
# Loads env from `.env` (in repo root) or `~/.tb-deploy.env`. Never puts
# secrets in the codebase.
#
# Prereq: remote Postgres must be running. Either:
#   ./pefeatures-build.sh   (deploys the full stack including postgres)
# or just postgres on the remote:
#   ssh container@192.168.69.16 "cd /home/container/thingsboard && docker compose up -d postgres"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Load env: prefer ~/.tb-deploy.env (out of repo), fall back to ./.env.
if [ -f "${HOME}/.tb-deploy.env" ]; then
  set -a; source "${HOME}/.tb-deploy.env"; set +a
elif [ -f "${REPO_ROOT}/.env" ]; then
  set -a; source "${REPO_ROOT}/.env"; set +a
else
  echo "No env file found. Copy deploy/.env.example to .env and fill it in." >&2
  exit 1
fi

: "${SPRING_DATASOURCE_URL:?must be set (see deploy/.env.example)}"

echo "▶ Running ThingsBoard against ${SPRING_DATASOURCE_URL}"
echo "▶ Server: http://${SERVER_ADDRESS:-127.0.0.1}:${SERVER_PORT:-8080}"

exec mvn -pl application spring-boot:run \
  -Dlicense.skip=true \
  -Dspring-boot.run.jvmArguments="-Dspring.datasource.url=${SPRING_DATASOURCE_URL} \
    -Dspring.datasource.username=${SPRING_DATASOURCE_USERNAME} \
    -Dspring.datasource.password=${SPRING_DATASOURCE_PASSWORD} \
    -Dserver.address=${SERVER_ADDRESS:-127.0.0.1} \
    -Dserver.port=${SERVER_PORT:-8080} \
    -Dqueue.type=${TB_QUEUE_TYPE:-in-memory} \
    -Dinstall.upgrade=false"
