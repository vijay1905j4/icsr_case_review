#!/usr/bin/env bash
# ops/run.sh — management script for the ICSR Case Review service.
#
# Usage:  ops/run.sh <command> [args]
#
# Commands:
#   build              Build the Docker image
#   start              Start the service (builds image first if absent)
#   stop               Stop and remove the container
#   test               Run the Maven test suite inside a build container
#   logs               Follow the container logs  (Ctrl-C to stop)
#   clean              Remove the container and image
#   backup             Snapshot the running case state to backups/
#   restore <file>     Reseed case_v1.json from a backup file, then prompt to rebuild
#   --help             Show this help and exit
#
# Requirements: Docker (docker info must succeed before any command runs)
# Runs on: Linux, macOS, and Windows Git Bash / WSL

set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────────

# Resolve the directory containing this script, then find the project root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
BACKUPS_DIR="${PROJECT_ROOT}/backups"

# ─── Configuration ────────────────────────────────────────────────────────────

IMAGE_NAME="icsr-case-review"
IMAGE_TAG="latest"
CONTAINER_NAME="icsr-api"
SERVICE_URL="http://localhost:8080"
SEED_FILE="${BACKEND_DIR}/src/main/resources/case_v1.json"
CASE_ID="PV-2026-0451"

# ─── Helpers ──────────────────────────────────────────────────────────────────

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Commands:
  build              Build the Docker image from backend/Dockerfile
  start              Start the service via docker-compose (builds first if needed)
  stop               Stop and remove the running container
  test               Run the Maven test suite inside a throwaway container
  logs               Follow the service container logs (Ctrl-C to exit)
  clean              Remove the container and image to reclaim disk space
  backup             Snapshot running case state to backups/ directory
  restore <file>     Reseed case_v1.json from a backup file; rebuild to apply
  --help, -h         Show this help and exit

Examples:
  ops/run.sh build
  ops/run.sh start
  ops/run.sh logs
  ops/run.sh backup
  ops/run.sh restore backups/case_PV-2026-0451_20260410T110000Z.json
  ops/run.sh clean
EOF
}

# Print an error to stderr and exit non-zero.
die() {
  echo "ERROR: $*" >&2
  exit 1
}

# Verify Docker is reachable before running any Docker command.
require_docker() {
  if ! docker info > /dev/null 2>&1; then
    die "Docker is not running. Start Docker Desktop (or the Docker daemon) and retry."
  fi
}

# Verify the compose file exists before any compose command.
require_compose_file() {
  if [ ! -f "${COMPOSE_FILE}" ]; then
    die "docker-compose.yml not found at ${COMPOSE_FILE}"
  fi
}

# ─── Command implementations ──────────────────────────────────────────────────

cmd_build() {
  require_docker
  echo "→ Building image ${IMAGE_NAME}:${IMAGE_TAG} ..."
  docker build \
    --tag "${IMAGE_NAME}:${IMAGE_TAG}" \
    "${BACKEND_DIR}"
  echo "✓ Image built: ${IMAGE_NAME}:${IMAGE_TAG}"
}

cmd_start() {
  require_docker
  require_compose_file
  echo "→ Starting service ..."
  # --build ensures the image is (re-)built if source has changed since the last run.
  # --detach returns immediately; the service runs in the background.
  docker compose --file "${COMPOSE_FILE}" up --build --detach
  echo "✓ Service started."
  echo "  Health : ${SERVICE_URL}/health"
  echo "  Logs   : ops/run.sh logs"
  echo "  Stop   : ops/run.sh stop"
}

cmd_stop() {
  require_docker
  require_compose_file
  echo "→ Stopping service ..."
  docker compose --file "${COMPOSE_FILE}" down
  echo "✓ Service stopped."
}

cmd_test() {
  require_docker
  echo "→ Running Maven test suite in a throwaway container ..."

  # Mount the host Maven cache read-only so Maven does not re-download
  # the entire dependency graph on every test run.
  # HOME may be unset in cron or restricted environments; check before using it.
  local m2_mount=()
  if [ -n "${HOME:-}" ] && [ -d "${HOME}/.m2" ]; then
    m2_mount=("--volume" "${HOME}/.m2:/root/.m2:ro")
  fi

  # The container is removed automatically after the tests complete (--rm).
  docker run \
    --rm \
    --volume "${BACKEND_DIR}:/workspace" \
    "${m2_mount[@]:-}" \
    --workdir /workspace \
    "maven:3.9-eclipse-temurin-21" \
    mvn test --batch-mode
  echo "✓ Tests complete."
}

cmd_logs() {
  require_docker
  require_compose_file
  # --follow tails the log stream; Ctrl-C exits without error.
  docker compose --file "${COMPOSE_FILE}" logs --follow
}

cmd_clean() {
  require_docker
  require_compose_file
  echo "→ Removing container and image ..."
  # --rmi local removes only images built by compose (not pulled base images).
  # --volumes removes any anonymous volumes associated with the service.
  docker compose --file "${COMPOSE_FILE}" down --rmi local --volumes
  echo "✓ Clean complete."
}

cmd_backup() {
  require_docker

  # Ensure the backups directory exists before writing to it.
  mkdir -p "${BACKUPS_DIR}"

  local timestamp
  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  local backup_file="${BACKUPS_DIR}/case_${CASE_ID}_${timestamp}.json"

  echo "→ Snapshotting case state from running service ..."

  # --fail:     treat HTTP errors as failures
  # --silent:   suppress progress meter
  # --max-time: abort if the service stalls rather than hanging indefinitely
  if ! curl --fail --silent --max-time 10 "${SERVICE_URL}/cases/${CASE_ID}" > "${backup_file}"; then
    rm -f "${backup_file}"
    die "Could not reach the service at ${SERVICE_URL}. Is it running? Try: ops/run.sh start"
  fi

  echo "✓ Backup saved to: ${backup_file}"
}

cmd_restore() {
  local backup_file="${1:-}"

  # Require the backup file path as an argument.
  if [ -z "${backup_file}" ]; then
    echo "Usage: $(basename "$0") restore <backup-file>" >&2
    echo "Example: ops/run.sh restore backups/case_PV-2026-0451_20260410T110000Z.json" >&2
    exit 1
  fi

  if [ ! -f "${backup_file}" ]; then
    die "Backup file not found: ${backup_file}"
  fi

  # Validate the backup is parseable JSON before overwriting the seed file.
  # Without this check, a corrupt or partial file (e.g. an HTML error page)
  # would silently replace case_v1.json and break the next startup.
  if ! command -v jq > /dev/null 2>&1; then
    die "jq is required for validation but is not installed. Aborting to protect the seed file."
  fi
  if ! jq -e . "${backup_file}" > /dev/null 2>&1; then
    die "Backup file is not valid JSON — refusing to overwrite seed file: ${backup_file}"
  fi

  echo "→ Restoring seed file from: ${backup_file} ..."
  cp "${backup_file}" "${SEED_FILE}"
  echo "✓ Seed file updated at: ${SEED_FILE}"
  echo ""
  echo "  The service loads case_v1.json at startup — not at runtime."
  echo "  To apply the restore, rebuild and restart:"
  echo "    ops/run.sh clean"
  echo "    ops/run.sh start"
}

# ─── Entry point ──────────────────────────────────────────────────────────────

if [ $# -eq 0 ]; then
  usage
  exit 1
fi

COMMAND="$1"
shift  # consume the command; remaining "$@" are arguments for the command

case "${COMMAND}" in
  build)         cmd_build ;;
  start)         cmd_start ;;
  stop)          cmd_stop ;;
  test)          cmd_test ;;
  logs)          cmd_logs ;;
  clean)         cmd_clean ;;
  backup)        cmd_backup ;;
  restore)       cmd_restore "${1:-}" ;;
  --help | -h)   usage; exit 0 ;;
  *)
    echo "ERROR: Unknown command: '${COMMAND}'" >&2
    echo "Run '$(basename "$0") --help' for a list of valid commands." >&2
    exit 1
    ;;
esac
