# ICSR Case Review — Backend

Spring Boot service for pharmacovigilance case review. Merges AI-extracted follow-up data onto a stored case and annotates every field with a diff removed status.

---

## Quick start

**Prerequisites:** Java 21, Maven 3.9+

```bash
cd backend
mvn spring-boot:run
```

Service starts on **http://localhost:8080** in ~2 seconds.  
First run downloads Maven plugin dependencies (~30 s one-time cost).

Verify it's up:

```bash
curl http://localhost:8080/health
# {"status":"UP"}
```

Run tests:

```bash
mvn test
```

---

## Endpoints

### 1 — Health check

```bash
curl http://localhost:8080/health
```

```json
{"status":"UP"}
```

---

### 2 — Get a case

```bash
curl http://localhost:8080/cases/PV-2026-0451
```

```json
{
  "case_id": "PV-2026-0451",
  "version": 1,
  "case_classification": "non-significant",
  "extracted_at": "2026-04-08T09:14:00Z",
  "source_document": "initial_report_PV-2026-0451.pdf",
  "sections": {
    "patient": {
      "age":      { "value": "62",   "confidence": 0.91, "source": "p.2 §1" },
      "initials": { "value": "M.K.", "confidence": 0.98, "source": "p.2 §1" }
    }
  }
}
```

Returns 404 with error body if `caseId` is not found.

---

### 3 — Post a follow-up (merge)

Every field in the response carries a `merge` annotation.

```bash
curl -X POST http://localhost:8080/cases/PV-2026-0451/follow-ups \
  -H "Content-Type: application/json" \
  -d '{
    "case_id": "PV-2026-0451",
    "version": 2,
    "case_classification": "significant",
    "extracted_at": "2026-04-15T11:00:00Z",
    "source_document": "followup_report_PV-2026-0451.pdf",
    "missing_fields": ["patient.weight_kg"],
    "sections": {
      "patient": {
        "initials": { "value": "M.K.", "confidence": 0.98, "source": "p.2 §1" },
        "age":      { "value": "63",   "confidence": 0.95, "source": "p.2 §1" }
      },
      "adverse_event": {
        "hospitalization": { "value": "Yes", "confidence": 0.91, "source": "p.4 §3" }
      }
    }
  }'
```

```json
{
  "case_id": "PV-2026-0451",
  "version": 2,
  "case_classification": "significant",
  "extracted_at": "2026-04-15T11:00:00Z",
  "source_document": "followup_report_PV-2026-0451.pdf",
  "missing_fields": ["patient.weight_kg"],
  "sections": {
    "patient": {
      "initials": {
        "value": "M.K.", "confidence": 0.98, "source": "p.2 §1",
        "merge": { "status": "unchanged" }
      },
      "age": {
        "value": "63", "confidence": 0.95, "source": "p.2 §1",
        "merge": { "status": "overridden", "previous_value": "62" }
      },
      "weight_kg": {
        "value": "78", "confidence": 0.85, "source": "p.3 §2",
        "merge": { "status": "retained" }
      }
    },
    "adverse_event": {
      "hospitalization": {
        "value": "Yes", "confidence": 0.91, "source": "p.4 §3",
        "merge": { "status": "new" }
      }
    }
  }
}
```

| `merge.status` | Meaning |
|---|---|
| `unchanged` | AI re-extracted; value matches stored |
| `overridden` | AI re-extracted; value changed — `previous_value` holds the old value |
| `new` | Field absent from stored version |
| `retained` | Field absent from follow-up — stored value preserved, not dropped |

---

### 4 — Raise a reviewer query

```bash
curl -X POST http://localhost:8080/queries \
  -H "Content-Type: application/json" \
  -d '{
    "case_id":    "PV-2026-0451",
    "field_path": "adverse_event.onset_date",
    "question":   "Onset date changed between versions — which is correct?"
  }'
```

```json
{
  "query_id":   "fa5add47-5923-4267-a138-cef25ffc2563",
  "case_id":    "PV-2026-0451",
  "field_path": "adverse_event.onset_date",
  "question":   "Onset date changed between versions — which is correct?",
  "created_at": "2026-04-15T11:05:00Z"
}
```

Returns `201 Created`. Returns 400 if any field is blank, 404 if `caseId` does not exist.

---

### 5 — List queries for a case

```bash
curl "http://localhost:8080/queries?caseId=PV-2026-0451"
```

```json
[
  {
    "query_id":   "fa5add47-5923-4267-a138-cef25ffc2563",
    "case_id":    "PV-2026-0451",
    "field_path": "adverse_event.onset_date",
    "question":   "Onset date changed between versions — which is correct?",
    "created_at": "2026-04-15T11:05:00Z"
  }
]
```

Returns 400 if `caseId` param is omitted, 404 if the case does not exist.

---

## Error responses

All errors use the same shape:

```json
{
  "status":    404,
  "error":     "NOT_FOUND",
  "message":   "Case not found: PV-XXXX",
  "path":      "/cases/PV-XXXX",
  "timestamp": "2026-04-15T11:05:00Z"
}
```

---

## Project layout

```
backend/
├── pom.xml
└── src/main/java/com/theragenx/icsr/
    ├── controller/   # HTTP routing, input validation
    ├── service/      # MergeService, QueryService
    ├── model/        # Java records: Case, AnnotatedField, MergeAnnotation, Query
    ├── store/        # In-memory CaseStore + QueryStore (ConcurrentHashMap)
    ├── exception/    # CaseNotFoundException, GlobalExceptionHandler
    └── config/       # Jackson config (snake_case, NON_NULL, ISO dates)
```

State is in-memory only. A restart reseeds from `src/main/resources/case_v1.json`.

---

## Operations runbook

> This section is written for someone who has never seen this service before.
> It covers every operation you are likely to need at 2 AM.

---

### Prerequisites

| Tool | Purpose | Install |
|---|---|---|
| Docker ≥ 24 | Build and run the container | https://docs.docker.com/get-docker/ |
| `jq` | Validate and parse JSON in ops scripts | `apt install jq` / `brew install jq` |
| `curl` | Health checks and backups | usually pre-installed |
| `make` | Thin convenience layer (optional) | usually pre-installed |

---

### Build and deploy

**First time or after a code change:**

```bash
# From the repo root
make build    # builds the Docker image
make start    # starts the container in the background
```

Or without Make:

```bash
ops/run.sh build
ops/run.sh start
```

The service is ready when `make start` prints:
```
Health : http://localhost:8080/health
```

**Confirm it is up:**

```bash
curl http://localhost:8080/health
# Expected: {"status":"UP"}
```

**Check the container status:**

```bash
docker ps --filter name=icsr-api
# CONTAINER ID   IMAGE                    STATUS                   PORTS
# a1b2c3d4e5f6   icsr-case-review:latest  Up 30 seconds (healthy)  0.0.0.0:8080->8080/tcp
```

The `(healthy)` label means the Docker healthcheck has passed at least once.
If it says `(health: starting)` wait 30 more seconds and check again.
If it says `(unhealthy)`, check the logs immediately (see below).

---

### Follow logs

```bash
make logs            # or: ops/run.sh logs
# Ctrl-C to stop following; the service keeps running.
```

---

### Stop the service

```bash
make stop            # or: ops/run.sh stop
```

This stops and removes the container. The image is kept. Run `make start` to bring it back up.

---

### Back up case state

The service holds state in memory. A container restart reseeds from `case_v1.json`.
Run a backup before any stop/restart to preserve follow-up data posted since the last restart.

```bash
make backup          # or: ops/run.sh backup
```

Writes to: `backups/case_PV-2026-0451_<UTC-timestamp>.json`

For a more detailed backup (validates JSON, cron-safe, lock-protected):

```bash
bash ops/backup.sh
```

To override the case IDs backed up:

```bash
CASE_IDS="PV-2026-0451 PV-2026-0452" bash ops/backup.sh
```

---

### Restore from backup

**Dry-run first — always:**

```bash
ops/restore.sh --dry-run backups/cases_20260410T110000Z.json
```

This prints exactly what would happen and exits without changing anything.

**Apply the restore:**

```bash
ops/restore.sh backups/cases_20260410T110000Z.json
```

This:
1. Validates the backup file is parseable JSON
2. Writes the case to `backend/src/main/resources/case_v1.json`
3. Stops the container (`docker compose down`)
4. Rebuilds the image (so the new seed file is baked in)
5. Starts the container (`docker compose up --build --detach`)
6. Verifies `/health` passes before exiting

The service will serve the restored case after it starts (~30 seconds).

---

### Debug a failed startup

**Step 1 — Check the container exited:**

```bash
docker ps -a --filter name=icsr-api
# Look for STATUS = Exited (1) or similar
```

**Step 2 — Read the exit logs:**

```bash
docker logs icsr-api --tail 50
```

**Common causes and fixes:**

| Symptom in logs | Cause | Fix |
|---|---|---|
| `Failed to load bootstrap case from classpath:case_v1.json` | `case_v1.json` is missing or malformed | Check `backend/src/main/resources/case_v1.json` with `jq . backend/src/main/resources/case_v1.json` |
| `Web server failed to start. Port 8080 was already in use.` | Another process holds port 8080 | `lsof -i :8080` to find and kill it, then `make start` |
| `Error: Unable to access jarfile app.jar` | The Maven build stage failed | Run `make build` and look for compilation errors |
| Container exits immediately with code 137 | OOM killed | Raise `-Xmx256m` in `docker-compose.yml` or free host memory |
| `Bootstrapped case 'PV-2026-0451' (version 1)` but then exits | Tomcat startup failure after bootstrap | Look for the line *after* the bootstrap log for the real cause |

**Step 3 — Rebuild from scratch if unsure:**

```bash
make clean   # removes the container and image
make build   # fresh build
make start
```

---

### What to check first when requests fail

Run these in order. Stop at the first failure.

**1. Is the service process alive?**

```bash
curl -sf http://localhost:8080/health
# {"status":"UP"} = alive. Move to step 2.
# Connection refused = process is down. Run: make start
```

**2. Is the case you're querying actually there?**

```bash
curl http://localhost:8080/cases/PV-2026-0451
# 200 = case is seeded. Move to step 3.
# 404 = case is missing. Check case_v1.json, then make clean && make start
```

**3. Is your request well-formed?**

All POST endpoints require `Content-Type: application/json`. Missing it returns:

```json
{ "status": 400, "error": "BAD_REQUEST", "message": "Request body is missing or contains malformed JSON" }
```

For `POST /cases/{id}/follow-ups`, the URL `{id}` must match the `case_id` in the JSON body.

**4. Read the full error body:**

```bash
curl -sv http://localhost:8080/cases/UNKNOWN 2>&1 | tail -20
```

Every error has the same shape — `status`, `error`, `message`, `path`, `timestamp`.
The `message` field is specific enough to diagnose the problem without reading source.

**5. Check container logs for stack traces:**

```bash
make logs
# or if the container is stopped:
docker logs icsr-api --tail 100
```

---

### Ops files reference

| File | What it does |
|---|---|
| `ops/run.sh` | Primary control script: `build start stop test logs clean backup restore` |
| `ops/backup.sh` | Cron-safe detailed backup with lock, HTTP status check, and JSON validation |
| `ops/restore.sh` | Validated restore with `--dry-run` support; stops, rebuilds, and restarts |
| `Makefile` | Thin convenience layer — every target delegates to `ops/run.sh` |
| `backend/Dockerfile` | Multi-stage build: Maven build → JRE-only runtime, non-root user |
| `docker-compose.yml` | Single-service stack with healthcheck and restart policy |

