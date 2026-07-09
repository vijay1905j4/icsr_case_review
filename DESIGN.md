# ICSR Case Review — Backend Design

## Assumptions

- The follow-up payload is the **same JSON shape** as `case_v1.json` (top-level case object with a `sections` map). Absence of a section or field key means the AI did not re-extract it — it is not a deletion.
- `missing_fields` in the follow-up payload is an **AI output annotation for that extraction run only**; it replaces, not accumulates, any previous `missing_fields`.
- Field identity is `sectionKey + "." + fieldKey` (e.g. `"patient.weight_kg"`). No deeper nesting.
- `caseId` in the URL must match the `case_id` in the payload; mismatch → 400.
- All state is in-memory. A restart resets to the bootstrapped `case_v1.json`; this is acceptable per the brief.
- Queries are append-only; no update or delete endpoint needed.

## Scope

**In**
- `GET /cases/{caseId}` — return stored case (no annotations)
- `POST /cases/{caseId}/follow-ups` — merge + annotate, persist merged result as new stored version
- `POST /queries` — create a reviewer query
- `GET /queries?caseId={id}` — list queries for a case
- `GET /health` — liveness check
- Bootstrap from `case_v1.json` on startup
- Input validation with 400 / 404 error responses
- ≥ 3 unit tests covering merge logic edge cases

**Out**
- Authentication, database, Kubernetes, Terraform
- Query update/delete, pagination
- Multi-case support beyond the bootstrapped case

## Missing-Field Decision

**Field present in stored version but absent from follow-up → `status: "retained"`**

The field is kept in the merged output with its existing value; no `previous_value` is set.

**Reasoning:** A follow-up is a partial re-extraction, not a full replacement. The AI omits a field because it couldn't find it in the new document — not because the data no longer exists. Dropping the field silently would destroy information in a regulatory context. `"retained"` is intentionally distinct from `"unchanged"` (which means the AI re-extracted the same value) so the reviewer can see exactly which fields were and were not revisited in this follow-up.

## Package Structure

```
com.theragenx.icsr
├── controller/    # HTTP layer — routing, @Valid, error responses (400/404)
├── service/       # Business logic — MergeService, QueryService; fully unit-testable
├── model/         # Java records — Case, Section, Field, AnnotatedField,
│                  #   MergeAnnotation, Query (no JPA, no inheritance)
├── store/         # In-memory state — CaseStore, QueryStore wrapping ConcurrentHashMap;
│                  #   loaded at startup via @PostConstruct
└── config/        # Jackson config (@JsonInclude NON_NULL); nothing else unless needed
```

## Merged-Response JSON Shape

The response to `POST /cases/{caseId}/follow-ups` is a full case object where every field carries a `merge` annotation block. Top-level `missing_fields` comes from the follow-up payload.

```json
{
  "case_id": "PV-2026-0451",
  "version": 2,
  "case_classification": "non-significant",
  "extracted_at": "2026-04-15T11:00:00Z",
  "source_document": "followup_report_PV-2026-0451.pdf",
  "missing_fields": ["patient.weight_kg"],
  "sections": {
    "patient": {
      "initials": {
        "value": "M.K.",
        "confidence": 0.98,
        "source": "p.2 §1",
        "merge": { "status": "unchanged" }
      },
      "age": {
        "value": "63",
        "confidence": 0.95,
        "source": "p.2 §1",
        "merge": { "status": "overridden", "previous_value": "62" }
      },
      "weight_kg": {
        "value": "78",
        "confidence": 0.85,
        "source": "p.3 §2",
        "merge": { "status": "retained" }
      }
    },
    "adverse_event": {
      "hospitalization": {
        "value": "Yes",
        "confidence": 0.91,
        "source": "p.4 §3",
        "merge": { "status": "new" }
      }
    }
  }
}
```

**Merge status summary**

| Status | Meaning |
|---|---|
| `unchanged` | AI re-extracted; value matches stored version |
| `overridden` | AI re-extracted; value differs — `previous_value` always present |
| `new` | Field exists in follow-up but was absent from stored version |
| `retained` | Field absent from follow-up; stored value preserved as-is |
