#!/usr/bin/env bash
# ops/restore.sh — restore case data from a backup file.
#
# Copies the backed-up case into case_v1.json (the bootstrap seed),
# then stops and restarts the service so the restored data takes effect.
#
# This operation is IDEMPOTENT: running it twice with the same file
# produces the same state.
#
# Usage:
#   ops/restore.sh [--dry-run] <backup-file>
#
# Options:
#   --dry-run   Validate the file and print what would happen; change nothing.
#
# Accepts both formats produced by this project:
#   - JSON array   (output of ops/backup.sh)     → restores the first element
#   - JSON object  (output of ops/run.sh backup)  → restores directly
#
# Exit codes:
#   0  success (or successful dry-run)
#   1  bad arguments or file not found
#   2  file is not valid JSON or is missing a required field
#   3  Docker or compose error during restart

set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SEED_FILE="${PROJECT_ROOT}/backend/src/main/resources/case_v1.json"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"

# ─── Helpers ──────────────────────────────────────────────────────────────────

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*" >&2
}

die() {
  log "ERROR: $*"
  exit "${2:-1}"
}

usage() {
  cat <<EOF
Usage: $(basename "$0") [--dry-run] <backup-file>

  --dry-run      Validate and print what would happen. Changes nothing.
  <backup-file>  Path to a backup produced by ops/backup.sh or ops/run.sh backup.

Examples:
  ops/restore.sh backups/cases_20260410T110000Z.json
  ops/restore.sh --dry-run backups/cases_20260410T110000Z.json
EOF
}

# ─── Argument parsing ─────────────────────────────────────────────────────────

DRY_RUN=false
BACKUP_FILE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --help | -h)
      usage
      exit 0
      ;;
    -*)
      die "Unknown option: '$1'. Run $(basename "$0") --help for usage." 1
      ;;
    *)
      # Reject a second positional argument.
      if [ -n "${BACKUP_FILE}" ]; then
        die "Too many arguments. Only one backup file may be specified." 1
      fi
      BACKUP_FILE="$1"
      shift
      ;;
  esac
done

if [ -z "${BACKUP_FILE}" ]; then
  usage >&2
  exit 1
fi

# ─── Preflight: tools ─────────────────────────────────────────────────────────

if ! command -v jq > /dev/null 2>&1; then
  die "jq is required but not installed." 1
fi

# ─── Preflight: backup file ───────────────────────────────────────────────────

if [ ! -f "${BACKUP_FILE}" ]; then
  die "Backup file not found: ${BACKUP_FILE}" 1
fi

if [ ! -r "${BACKUP_FILE}" ]; then
  die "Backup file is not readable: ${BACKUP_FILE}" 1
fi

# The file must be parseable JSON before we do anything else.
if ! jq -e . "${BACKUP_FILE}" > /dev/null 2>&1; then
  die "Backup file is not valid JSON: ${BACKUP_FILE}" 2
fi

# Determine whether the file is an array (from backup.sh) or a single object
# (from run.sh backup or a manually created file).
FILE_TYPE="$(jq -r 'type' "${BACKUP_FILE}")"

case "${FILE_TYPE}" in
  array)
    # Array format from backup.sh: restore the first element.
    CASE_COUNT="$(jq 'length' "${BACKUP_FILE}")"

    if [ "${CASE_COUNT}" -eq 0 ]; then
      die "Backup array is empty — nothing to restore." 2
    fi

    RESTORE_JSON="$(jq '.[0]' "${BACKUP_FILE}")"
    ;;
  object)
    # Single-object format: restore directly.
    CASE_COUNT=1
    RESTORE_JSON="$(jq '.' "${BACKUP_FILE}")"
    ;;
  *)
    die "Backup file contains neither a JSON array nor a JSON object (got: ${FILE_TYPE})." 2
    ;;
esac

# The case to restore must have a case_id field.
RESTORE_CASE_ID="$(echo "${RESTORE_JSON}" | jq -r '.case_id // empty')"

if [ -z "${RESTORE_CASE_ID}" ]; then
  die "The case in the backup has no 'case_id' field. Cannot restore." 2
fi

RESTORE_VERSION="$(echo "${RESTORE_JSON}" | jq -r '.version // "unknown"')"

# ─── Dry-run output ───────────────────────────────────────────────────────────

if [ "${DRY_RUN}" = "true" ]; then
  echo ""
  echo "  DRY RUN — no changes will be made"
  echo ""
  echo "  Backup file  : ${BACKUP_FILE}"
  echo "  Format       : ${FILE_TYPE} (${CASE_COUNT} case(s) in file)"
  echo "  Would restore: case_id=${RESTORE_CASE_ID}  version=${RESTORE_VERSION}"
  echo "  Seed file    : ${SEED_FILE}"
  echo ""
  echo "  Steps that would run:"
  echo "    1. Write case ${RESTORE_CASE_ID} (v${RESTORE_VERSION}) to ${SEED_FILE}"
  echo "    2. docker compose --file ${COMPOSE_FILE} down"
  echo "    3. docker compose --file ${COMPOSE_FILE} up --build --detach"
  echo "    4. Verify: curl ${SERVICE_URL}/health"
  echo ""
  echo "  Run without --dry-run to apply."
  exit 0
fi

# ─── Restore ──────────────────────────────────────────────────────────────────

log "Starting restore from: ${BACKUP_FILE}"
log "  Case to restore: ${RESTORE_CASE_ID} (version ${RESTORE_VERSION})"

# Write the case JSON to the seed file, pretty-printed so it's human-readable
# and produces clean diffs in version control.
echo "${RESTORE_JSON}" | jq '.' > "${SEED_FILE}"
log "  ✓ Seed file written: ${SEED_FILE}"

# Verify Docker is reachable before attempting to restart.
if ! docker info > /dev/null 2>&1; then
  log "WARNING: Docker is not running."
  log "  Seed file has been updated. Once Docker is running:"
  log "    docker compose --file ${COMPOSE_FILE} up --build --detach"
  exit 0
fi

# Verify the compose file exists before calling docker compose.
if [ ! -f "${COMPOSE_FILE}" ]; then
  die "docker-compose.yml not found at ${COMPOSE_FILE}. Cannot restart." 3
fi

# Stop the running container so the next start picks up the new seed file.
log "  Stopping running service ..."
docker compose --file "${COMPOSE_FILE}" down

# Rebuild (bakes the new case_v1.json into the image) and start detached.
log "  Rebuilding and starting service ..."
docker compose --file "${COMPOSE_FILE}" up --build --detach

log "Service started. Waiting 10 seconds for the JVM to initialise ..."
sleep 10

# Verify the service is healthy and serving the restored case.
log "  Verifying health ..."
if ! curl --fail --silent --max-time 5 "${SERVICE_URL}/health" > /dev/null 2>&1; then
  log "WARNING: Service did not pass health check within 10 seconds."
  log "         Check logs:  ops/run.sh logs"
  exit 3
fi

log "  ✓ Service is healthy."
log "  Verify case: curl ${SERVICE_URL}/cases/${RESTORE_CASE_ID}"
log "Restore complete."
