#!/usr/bin/env bash
#
# pefeatures-build.sh
#
# Build the PE-feature-enabled ThingsBoard tb-node image on this Mac, ship it to
# the remote test server, and bring the stack up there with docker compose.
#
# Steps:
#   1. mvn package the project (produces application/target/thingsboard.deb)
#   2. mvn build the tb-node Docker image
#   3. docker build the tb-web-report sidecar (Node + puppeteer)
#   4. docker save | gzip both images into tarballs
#   5. scp tarballs + deploy/docker-compose.yml to the remote
#   6. ssh remote: docker load each tarball, docker compose down/up -d
#
# Credentials are read from environment variables only — never hardcode them
# in this file, in committed config, or in commit messages.
#
# Required env vars (set in your shell or a .env file you source):
#   TB_DEPLOY_HOST       e.g. 192.168.69.16
#   TB_DEPLOY_USER       remote SSH user (e.g. container or root)
#   TB_DEPLOY_PATH       target dir on the remote (e.g. /home/container/thingsboard)
#   TB_IMAGE_TAG         desired local image tag (default: thingsboard/tb-node:pe-local)
# Auth: pick exactly one
#   TB_DEPLOY_KEY        path to private SSH key (recommended)
#   TB_DEPLOY_PASSWORD   password (requires `sshpass` installed; only as fallback)
#
# Optional:
#   SKIP_BUILD=1         reuse an existing image tagged TB_IMAGE_TAG (skip mvn)
#   SKIP_WEB_REPORT=1    don't build or ship the tb-web-report sidecar
#   SKIP_DEPLOY=1        build only — don't ship to the server
#   SKIP_TESTS=1         pass -DskipTests to maven (default: 1)
#   MAVEN_OPTS           passed through to maven
#   TB_WEB_REPORT_TAG    web-report image tag (default: thingsboard/tb-web-report:pe-local)

set -euo pipefail

# ---------------------------------------------------------------- config

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TB_IMAGE_TAG="${TB_IMAGE_TAG:-thingsboard/tb-node:pe-local}"
TB_WEB_REPORT_TAG="${TB_WEB_REPORT_TAG:-thingsboard/tb-web-report:pe-local}"
SKIP_BUILD="${SKIP_BUILD:-0}"
SKIP_WEB_REPORT="${SKIP_WEB_REPORT:-0}"
SKIP_DEPLOY="${SKIP_DEPLOY:-0}"
SKIP_TESTS="${SKIP_TESTS:-1}"
ARTIFACT_DIR="${SCRIPT_DIR}/build-artifacts"
ARTIFACT_NAME="tb-node-pe.tar.gz"
ARTIFACT_PATH="${ARTIFACT_DIR}/${ARTIFACT_NAME}"
WEB_REPORT_ARTIFACT_NAME="tb-web-report-pe.tar.gz"
WEB_REPORT_ARTIFACT_PATH="${ARTIFACT_DIR}/${WEB_REPORT_ARTIFACT_NAME}"
WEB_REPORT_CTX="${SCRIPT_DIR}/tb-web-report"
COMPOSE_FILE="${SCRIPT_DIR}/deploy/docker-compose.yml"

# ---------------------------------------------------------------- logging

log()  { printf '\033[1;34m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }
warn() { printf '\033[1;33m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fail() { printf '\033[1;31m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*" >&2; exit 1; }

# ---------------------------------------------------------------- preflight

[ -f "${SCRIPT_DIR}/pom.xml" ] || fail "Must run from the ThingsBoard repo root (no pom.xml here)"
command -v mvn >/dev/null    || fail "maven not installed"
command -v docker >/dev/null || fail "docker not installed"

if [ "${SKIP_DEPLOY}" != "1" ]; then
  : "${TB_DEPLOY_HOST:?TB_DEPLOY_HOST not set}"
  : "${TB_DEPLOY_USER:?TB_DEPLOY_USER not set}"
  : "${TB_DEPLOY_PATH:?TB_DEPLOY_PATH not set}"
  [ -f "${COMPOSE_FILE}" ] || fail "Missing ${COMPOSE_FILE}"
fi

# ---------------------------------------------------------------- ssh helper

# Pick auth mode once and route every ssh/scp call through the same helpers.
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30"
if [ -n "${TB_DEPLOY_KEY:-}" ]; then
  log "SSH auth: key file (${TB_DEPLOY_KEY})"
  [ -f "${TB_DEPLOY_KEY}" ] || fail "TB_DEPLOY_KEY points to missing file: ${TB_DEPLOY_KEY}"
  SSH_OPTS="${SSH_OPTS} -i ${TB_DEPLOY_KEY}"
  ssh_remote() { ssh ${SSH_OPTS} "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}" "$@"; }
  scp_remote() { scp ${SSH_OPTS} "$@"; }
elif [ -n "${TB_DEPLOY_PASSWORD:-}" ]; then
  command -v sshpass >/dev/null || fail "sshpass not installed (brew install sshpass) — or set TB_DEPLOY_KEY instead"
  log "SSH auth: password (via sshpass)"
  ssh_remote() { sshpass -e ssh ${SSH_OPTS} "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}" "$@"; }
  scp_remote() { sshpass -e scp ${SSH_OPTS} "$@"; }
  export SSHPASS="${TB_DEPLOY_PASSWORD}"
elif [ "${SKIP_DEPLOY}" != "1" ]; then
  fail "No auth: set TB_DEPLOY_KEY (preferred) or TB_DEPLOY_PASSWORD"
fi

# ---------------------------------------------------------------- build

build() {
  if [ "${SKIP_BUILD}" = "1" ]; then
    log "SKIP_BUILD=1 → reusing existing image ${TB_IMAGE_TAG}"
    docker image inspect "${TB_IMAGE_TAG}" >/dev/null 2>&1 \
      || fail "No image tagged ${TB_IMAGE_TAG} — unset SKIP_BUILD and rerun"
    return
  fi

  local mvn_flags="-Dlicense.skip=true"
  [ "${SKIP_TESTS}" = "1" ] && mvn_flags="${mvn_flags} -DskipTests"

  log "mvn install (full project, produces application/target/thingsboard.deb)"
  ( cd "${SCRIPT_DIR}" && mvn clean install ${mvn_flags} )

  log "mvn build tb-node Docker image"
  # When invoking via -f, maven.multiModuleProjectDirectory points at msa/tb-node/
  # so ${main.dir} mis-resolves. Pin main.dir to the repo root explicitly.
  ( cd "${SCRIPT_DIR}" && mvn -f msa/tb-node/pom.xml verify \
      -DskipTests \
      -Dlicense.skip=true \
      -Dmain.dir="${SCRIPT_DIR}" \
      -Ddockerfile.skip=false )

  # The Maven build tags as thingsboard/tb-node:latest + thingsboard/tb-node:<version>
  log "Tagging local image as ${TB_IMAGE_TAG}"
  docker tag thingsboard/tb-node:latest "${TB_IMAGE_TAG}"
}

build_web_report() {
  if [ "${SKIP_WEB_REPORT}" = "1" ]; then
    log "SKIP_WEB_REPORT=1 → skipping tb-web-report image build"
    return
  fi
  [ -d "${WEB_REPORT_CTX}" ] || { warn "no tb-web-report/ directory found, skipping"; return; }
  log "Building tb-web-report image (${TB_WEB_REPORT_TAG})"
  # Force linux/amd64 even on Apple Silicon so the image runs on the x86_64 deploy host.
  ( cd "${WEB_REPORT_CTX}" && \
    DOCKER_DEFAULT_PLATFORM="${DOCKER_DEFAULT_PLATFORM:-linux/amd64}" \
    docker build --platform "${DOCKER_DEFAULT_PLATFORM:-linux/amd64}" \
      -t "${TB_WEB_REPORT_TAG}" . )
}

# ---------------------------------------------------------------- package

package() {
  mkdir -p "${ARTIFACT_DIR}"
  log "Saving tb-node image to ${ARTIFACT_PATH}"
  docker save "${TB_IMAGE_TAG}" | gzip -9 > "${ARTIFACT_PATH}"
  local size
  size=$(du -h "${ARTIFACT_PATH}" | cut -f1)
  log "Artifact ready: ${ARTIFACT_PATH} (${size})"

  if [ "${SKIP_WEB_REPORT}" != "1" ] && docker image inspect "${TB_WEB_REPORT_TAG}" >/dev/null 2>&1; then
    log "Saving tb-web-report image to ${WEB_REPORT_ARTIFACT_PATH}"
    docker save "${TB_WEB_REPORT_TAG}" | gzip -9 > "${WEB_REPORT_ARTIFACT_PATH}"
    local wsize
    wsize=$(du -h "${WEB_REPORT_ARTIFACT_PATH}" | cut -f1)
    log "Artifact ready: ${WEB_REPORT_ARTIFACT_PATH} (${wsize})"
  fi
}

# ---------------------------------------------------------------- deploy

deploy() {
  if [ "${SKIP_DEPLOY}" = "1" ]; then
    log "SKIP_DEPLOY=1 → done (artifact at ${ARTIFACT_PATH})"
    return
  fi

  log "Ensuring remote directory exists: ${TB_DEPLOY_PATH}"
  ssh_remote "mkdir -p '${TB_DEPLOY_PATH}'"

  log "Copying tb-node image archive to remote (${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/)"
  scp_remote "${ARTIFACT_PATH}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/${ARTIFACT_NAME}"

  if [ -f "${WEB_REPORT_ARTIFACT_PATH}" ]; then
    log "Copying tb-web-report image archive to remote"
    scp_remote "${WEB_REPORT_ARTIFACT_PATH}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/${WEB_REPORT_ARTIFACT_NAME}"
  fi

  log "Copying docker-compose.yml to remote"
  scp_remote "${COMPOSE_FILE}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/docker-compose.yml"

  log "Remote: loading image(s), recycling stack"
  # heredoc — runs as a single ssh session on the remote
  ssh_remote bash -se <<REMOTE
set -euo pipefail
cd "${TB_DEPLOY_PATH}"

# Pick docker compose flavor (plugin vs legacy binary).
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "ERROR: docker compose not installed on remote" >&2
  exit 1
fi

echo "  → docker load ${ARTIFACT_NAME}"
gunzip -c "${ARTIFACT_NAME}" | docker load

if [ -f "${WEB_REPORT_ARTIFACT_NAME}" ]; then
  echo "  → docker load ${WEB_REPORT_ARTIFACT_NAME}"
  gunzip -c "${WEB_REPORT_ARTIFACT_NAME}" | docker load
fi

echo "  → \${COMPOSE} down (existing stack, if any)"
\${COMPOSE} down --remove-orphans || true

echo "  → \${COMPOSE} up -d"
\${COMPOSE} up -d

echo "  → docker ps"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE

  log "Deploy complete. UI:  http://${TB_DEPLOY_HOST}:8000  (default tenant: tenant@thingsboard.org / tenant)"
}

# ---------------------------------------------------------------- main

main() {
  log "tb-image-tag = ${TB_IMAGE_TAG}"
  log "tb-web-report-tag = ${TB_WEB_REPORT_TAG}"
  build
  build_web_report
  package
  deploy
  log "Done."
}

main "$@"
