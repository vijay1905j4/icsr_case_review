# ICSR Case Review — Backend

A Spring Boot service that powers pharmacovigilance case review. Accepts AI-extracted case data, merges follow-up updates with diff annotations, and surfaces reviewer queries.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Set `JAVA_HOME` to JDK 21 |
| Maven | 3.9+ | Available at `~/.m2/wrapper/dists/` or install separately |

---

## Running Locally

```bash
cd backend

# If mvn is on PATH:
mvn spring-boot:run

# Windows — full path if mvn is not on PATH:
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
& "C:\Users\<you>\.m2\wrapper\dists\apache-maven-3.9.11-bin\...\bin\mvn.cmd" spring-boot:run
```

Service starts on **http://localhost:8080**. First run downloads Maven plugin artifacts (~30s); subsequent starts are ~2s.

---

## Verifying the Service is Healthy

```bash
curl http://localhost:8080/health
# → {"status":"UP"}
```

---

## API — curl Examples

> **Note:** The bootstrap case ID is `PV-2026-0451`. POST endpoints require `Content-Type: application/json`.

### 1. Get a case
```bash
curl -s http://localhost:8080/cases/PV-2026-0451
```

### 2. Post a follow-up (merge + diff annotations)
```bash
curl -s -X POST http://localhost:8080/cases/PV-2026-0451/follow-ups \
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

### 3. Raise a reviewer query on a field
```bash
curl -s -X POST http://localhost:8080/queries \
  -H "Content-Type: application/json" \
  -d '{
    "case_id": "PV-2026-0451",
    "field_path": "adverse_event.onset_date",
    "question": "The onset date changed between versions — can you confirm which is correct?"
  }'
```

### 4. List all queries for a case
```bash
curl -s "http://localhost:8080/queries?caseId=PV-2026-0451"
```

---

## Merge Behaviour

When a follow-up is POSTed, every field in the merged response carries a `merge` annotation:

| `status` | When |
|---|---|
| `unchanged` | AI re-extracted; value matches stored version |
| `overridden` | AI re-extracted; value differs — `previous_value` included |
| `new` | Field present in follow-up but absent from stored version |
| `retained` | Field absent from follow-up — stored value preserved as-is |

Fields absent from the follow-up are **retained** (not dropped). A follow-up is a partial re-extraction, not a replacement. See `DESIGN.md` for full reasoning.

---

## Project Structure

```
backend/
├── pom.xml
└── src/
    ├── main/java/com/theragenx/icsr/
    │   ├── IcsrApplication.java
    │   ├── config/          # Jackson config
    │   ├── controller/      # HTTP layer (CaseController, QueryController, HealthController)
    │   ├── exception/       # CaseNotFoundException → 404
    │   ├── model/           # Java records: Case, AnnotatedField, MergeAnnotation, Query
    │   ├── service/         # MergeService, QueryService
    │   └── store/           # In-memory CaseStore, QueryStore (ConcurrentHashMap)
    └── main/resources/
        ├── application.properties
        └── case_v1.json     # Bootstrap data loaded on startup
```

---

## Running Tests

```bash
mvn test
```

Tests live in `src/test/java/com/theragenx/icsr/service/MergeServiceTest.java` and cover all four merge status cases.

---

## Operations

> See [Operations Runbook](#operations-runbook) below — to be expanded in Phase 1B with Docker, backup/restore, and ops scripts.

### Operations Runbook

**Build and start**
```bash
# Phase 1B: docker-compose up --build
# Phase 1A: mvn spring-boot:run (see above)
```

**Verify healthy**
```bash
curl http://localhost:8080/health
```

**Debug a failed startup**
1. Check `JAVA_HOME` points to JDK 21 — not JDK 25 or 8
2. Confirm port 8080 is free: `netstat -ano | findstr :8080`
3. Check `src/main/resources/case_v1.json` is on the classpath (exists in `target/classes/`)

**What to check first if requests are failing**
1. `GET /health` — confirms the process is alive
2. Check the request `Content-Type: application/json` header is set for POST endpoints
3. Confirm `caseId` in the URL matches `case_id` in the JSON body for follow-ups
4. Look for `UnsupportedOperationException` in logs — means an endpoint stub hasn't been implemented yet
