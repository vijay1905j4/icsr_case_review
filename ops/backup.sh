#!/usr/bin/env bash
# ops/backup.sh — snapshot all known cases from the running service.
#
# Writes:  backups/cases_<timestamp>.json   (a JSON array of case objects)
# Logs:    stderr with UTC timestamps — safe to redirect in cron
# Exit codes:
#   0  success, file written
#   1  precondition failure (jq/curl missing, lock held, bad config)
#   2  service unreachable or returned an HTTP error
#   3  response was empty or not valid JSON
#
# Usage:
#   ops/backup.sh
#   CASE_IDS="PV-2026-0451 PV-2026-0452" ops/backup.sh
#
# Safe for cron: logs go to stderr, no interactive prompts, lock prevents overlap.
# Runs on: Linux, macOS, and Windows Git Bash / WSL.

set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUPS_DIR="${PROJECT_ROOT}/backups"

# Use /tmp for the lock so it survives across project-root mounts.
LOCK_DIR="/tmp/icsr-backup.lock"

# ─── Configuration (overridable via environment) ──────────────────────────────

SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"

# Space-separated list of case IDs to back up.
# Word-splitting on this variable is intentional — each token is a case ID.
CASE_IDS="${CASE_IDS:-PV-2026-0451}"

# ─── Helpers ──────────────────────────────────────────────────────────────────

# All log messages go to stderr so stdout is clean for piping/redirecting.
log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*" >&2
}

die() {
  log "ERROR: $*"
  exit "${2:-1}"
}

# ─── Lock: prevent concurrent backup runs (important in cron environments) ────

# mkdir is atomic on POSIX filesystems; it fails if the directory already exists.
if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
  die "Another backup is already running (lock: ${LOCK_DIR}). Exiting without backup." 1
fi

# Remove the lock on any exit — normal, error, or signal.
trap 'rmdir "${LOCK_DIR}" 2>/dev/null || true' EXIT

# ─── Preflight: tool availability ─────────────────────────────────────────────

if ! command -v curl > /dev/null 2>&1; then
  die "curl is required but not installed. Install it and retry." 1
fi

if ! command -v jq > /dev/null 2>&1; then
  die "jq is required but not installed. Install it and retry." 1
fi

# ─── Preflight: service health ────────────────────────────────────────────────

log "Checking service health at ${SERVICE_URL}/health ..."

# --fail:     treat HTTP errors as failures (non-zero exit)
# --silent:   suppress progress meter
# --max-time: don't hang indefinitely if the service is stuck
if ! curl --fail --silent --max-time 5 "${SERVICE_URL}/health" > /dev/null 2>&1; then
  log "Service is not reachable at ${SERVICE_URL}."
  log "Start it first:  ops/run.sh start  or  make start"
  exit 2
fi

log "Service is healthy. Starting backup."

# ─── Backup ───────────────────────────────────────────────────────────────────

mkdir -p "${BACKUPS_DIR}"

TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
FINAL_FILE="${BACKUPS_DIR}/cases_${TIMESTAMP}.json"
TEMP_FILE="${BACKUPS_DIR}/cases_${TIMESTAMP}.tmp"

# Clean up the temp file on any failure after this point.
trap 'rm -f "${TEMP_FILE}"; rmdir "${LOCK_DIR}" 2>/dev/null || true' EXIT

CASE_COUNT=0
FIRST_CASE=true

# Open the JSON array.
printf '[\n' > "${TEMP_FILE}"

# shellcheck disable=SC2086  # Word-splitting on CASE_IDS is intentional.
for CASE_ID in ${CASE_IDS}; do
  log "  Fetching case ${CASE_ID} ..."

  # Capture body separately from the HTTP status code.
  # --write-out sends the status to stdout after the body, so we redirect the body
  # to a temp file and read the status from the command substitution.
  CASE_TEMP="${BACKUPS_DIR}/.case_${CASE_ID}.tmp"

  HTTP_STATUS="$(
    curl \
      --silent \
      --write-out '%{http_code}' \
      --output "${CASE_TEMP}" \
      --max-time 10 \
      "${SERVICE_URL}/cases/${CASE_ID}" \
    2>/dev/null
  )" || true   # We check HTTP_STATUS explicitly; don't let curl exit abort the loop.

  # Guard: non-200 HTTP status.
  if [ "${HTTP_STATUS}" != "200" ]; then
    rm -f "${CASE_TEMP}"
    log "ERROR: HTTP ${HTTP_STATUS} fetching case ${CASE_ID}. Aborting backup."
    exit 2
  fi

  # Guard: empty response body.
  if [ ! -s "${CASE_TEMP}" ]; then
    rm -f "${CASE_TEMP}"
    log "ERROR: Empty response body for case ${CASE_ID}. Aborting backup."
    exit 3
  fi

  # Guard: response must be valid JSON.
  if ! jq -e . "${CASE_TEMP}" > /dev/null 2>&1; then
    log "ERROR: Response for case ${CASE_ID} is not valid JSON. Aborting backup."
    log "       Raw response:"
    cat "${CASE_TEMP}" >&2
    rm -f "${CASE_TEMP}"
    exit 3
  fi

  # Write comma separator between array elements (not before the first).
  if [ "${FIRST_CASE}" = "true" ]; then
    FIRST_CASE=false
  else
    printf ',\n' >> "${TEMP_FILE}"
  fi

  # Append the case JSON, pretty-printed, to the array.
  jq '.' "${CASE_TEMP}" >> "${TEMP_FILE}"
  rm -f "${CASE_TEMP}"

  CASE_COUNT=$((CASE_COUNT + 1))
  log "  ✓ Case ${CASE_ID} written."
done

# Close the JSON array.
printf '\n]\n' >> "${TEMP_FILE}"

# Final validation: the assembled file must be a non-empty JSON array.
if ! jq -e 'type == "array" and length > 0' "${TEMP_FILE}" > /dev/null 2>&1; then
  log "ERROR: Assembled backup file failed final JSON validation. Aborting."
  exit 3
fi

# Atomic promotion: rename temp → final.
# The final file either appears complete or not at all.
mv "${TEMP_FILE}" "${FINAL_FILE}"

# Reset trap now that TEMP_FILE no longer exists.
trap 'rmdir "${LOCK_DIR}" 2>/dev/null || true' EXIT

log "Backup complete: ${CASE_COUNT} case(s) → ${FINAL_FILE}"
