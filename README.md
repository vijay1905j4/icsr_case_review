# ICSR Case Review — Backend

Spring Boot service for pharmacovigilance case review. Merges AI-extracted follow-up data onto a stored case and annotates every field with a diff status.

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
