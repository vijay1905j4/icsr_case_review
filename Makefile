# Makefile — thin delegation layer over ops/run.sh.
#
# Every target here is a one-line delegate; all logic lives in ops/run.sh.
# This keeps the Makefile readable at a glance and avoids duplicating shell logic.
#
# Usage:
#   make help
#   make build
#   make start
#   make restore FILE=backups/case_PV-2026-0451_20260410T110000Z.json

.PHONY: build start stop test logs clean backup restore help

# Use bash so scripts can use bash-specific features (set -euo pipefail, etc.)
SHELL := /usr/bin/env bash

OPS := ops/run.sh

# ─── Default target ───────────────────────────────────────────────────────────

help:
	@echo ""
	@echo "ICSR Case Review — available make targets"
	@echo ""
	@echo "  build    Build the Docker image"
	@echo "  start    Build (if needed) and start the service in the background"
	@echo "  stop     Stop and remove the running container"
	@echo "  test     Run the Maven test suite inside a container"
	@echo "  logs     Follow service logs  (Ctrl-C to exit)"
	@echo "  clean    Remove the container and image"
	@echo "  backup   Snapshot running case state to backups/"
	@echo "  restore  Restore from a backup  (pass FILE=<path>)"
	@echo ""
	@echo "Examples:"
	@echo "  make start"
	@echo "  make restore FILE=backups/case_PV-2026-0451_20260410T110000Z.json"
	@echo ""

# ─── Service lifecycle ────────────────────────────────────────────────────────

build:
	@bash "$(OPS)" build

start:
	@bash "$(OPS)" start

stop:
	@bash "$(OPS)" stop

test:
	@bash "$(OPS)" test

logs:
	@bash "$(OPS)" logs

clean:
	@bash "$(OPS)" clean

# ─── Backup and restore ───────────────────────────────────────────────────────

backup:
	@bash "$(OPS)" backup

# FILE must be supplied by the caller: make restore FILE=backups/....json
restore:
ifndef FILE
	@echo "ERROR: FILE is required.  Usage: make restore FILE=<backup-path>" >&2
	@exit 1
endif
	@bash "$(OPS)" restore "$(FILE)"
