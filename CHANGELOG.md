# Changelog

## [2026-07-13] — Auto-apply v2 Follow-up on Startup

### Context

The brief requires that before the Phase 2 live session, `case_v2_followup_payload.json`
is POSTed to the backend so the merged case is immediately queryable. Rather than
doing this manually every time the service restarts, the bootstrap logic was extended to
apply the follow-up automatically at startup.

---

### Changes

#### `backend/src/main/resources/case_v2_followup_payload.json` *(new)*

Copied `assignment_phase_1/case_v2_followup_payload.json` onto the Spring Boot classpath
(`src/main/resources/`) so it can be loaded as a `ClassPathResource` during startup —
same pattern already used for `case_v1.json`.

---

#### `backend/src/main/java/com/theragenx/icsr/store/CaseStore.java` *(modified)*

**Before:** `@PostConstruct init()` loaded `case_v1.json` and seeded the in-memory store
with the raw version-1 case. Constructor accepted only `ObjectMapper`.

**After:** Two-phase bootstrap with a feature toggle:

1. **Step 1 (unchanged)** — Load `case_v1.json` → store as version 1.
2. **Step 2 (new, optional)** — If `icsr.bootstrap.apply-followup=true`, load
   `case_v2_followup_payload.json` and run it through `MergeService.merge()`.
   The annotated version-2 merged case replaces the v1 entry in the store.

Constructor now also accepts `MergeService` (injected by Spring). No circular dependency
risk — `MergeService` has no dependency back on `CaseStore`.

A `loadCaseFromClasspath(String resourceName)` private helper was extracted to avoid
duplicating the try/catch + `ClassPathResource` pattern for both files.

Key additions:

```java
// New constructor parameter
public CaseStore(ObjectMapper objectMapper, MergeService mergeService)

// Feature toggle — reads from application.properties, defaults to true
@Value("${icsr.bootstrap.apply-followup:true}")
private boolean applyFollowUp;

// Step 2 in @PostConstruct
if (applyFollowUp) {
    Case followUp = loadCaseFromClasspath("case_v2_followup_payload.json");
    Case merged   = mergeService.merge(bootstrapCase, followUp);
    cases.put(merged.caseId(), merged);
}
```

---

#### `backend/src/main/resources/application.properties` *(modified)*

Added the toggle property with inline documentation:

```properties
# Bootstrap: automatically apply case_v2_followup_payload.json on startup so the
# merged case (version 2, with full diff annotations) is immediately queryable.
# Set to false to restore v1-only boot behaviour (e.g. for integration tests).
icsr.bootstrap.apply-followup=true
```

---

#### `backend/src/test/java/com/theragenx/icsr/store/CaseStoreTest.java` *(modified)*

Updated existing tests and added 6 new ones to cover the merged v2 boot path.

| Test | Mode | Asserts |
|---|---|---|
| `v1OnlyBoot_caseIsPresent` | `applyFollowUp=false` | Case loads |
| `v1OnlyBoot_hasVersionOne` | `applyFollowUp=false` | `version == 1` |
| `v1OnlyBoot_patientSectionDeserialised` | `applyFollowUp=false` | Fields present |
| `unknownCaseId_returnsEmpty` | `applyFollowUp=false` | `Optional.empty()` |
| `v2MergedBoot_caseIsVersion2` | `applyFollowUp=true` | `version == 2` |
| `v2MergedBoot_doseIsOverridden` | `applyFollowUp=true` | `dose = "40 mg"`, `previous_value = "20 mg"`, `status = "overridden"` |
| `v2MergedBoot_hospitalizationIsNew` | `applyFollowUp=true` | `hospitalization = "Yes - 5 days"`, `status = "new"` |
| `v2MergedBoot_missingFieldsPopulated` | `applyFollowUp=true` | All 3 missing fields present |
| `v2MergedBoot_unchangedFieldHasNoAnnotationStatus` | `applyFollowUp=true` | `initials` is `"unchanged"`, no `previous_value` |
| `v2MergedBoot_eventTermIsOverridden` | `applyFollowUp=true` | `event_term = "Rhabdomyolysis"`, `previous_value = "Myalgia"` |

Test result: **15 / 15 passed** (`MergeServiceTest`: 5, `CaseStoreTest`: 10).

---

### v1 → v2 Field Diff

| Section | Field | v1 Value | v2 Value | Merge Status |
|---|---|---|---|---|
| `patient` | `weight_kg` | `78` | `76` | `overridden` |
| `suspect_drug` | `dose` | `20 mg` | `40 mg` | `overridden` |
| `adverse_event` | `event_term` | `Myalgia` | `Rhabdomyolysis` | `overridden` |
| `adverse_event` | `outcome` | `Recovered` | `Recovering` | `overridden` |
| `adverse_event` | `seriousness` | `Non-serious` | `Serious` | `overridden` |
| `adverse_event` | `hospitalization` | *(absent)* | `Yes - 5 days` | `new` |
| `patient`, `suspect_drug`, `reporter` | all others | same | same | `unchanged` |
| top-level | `missing_fields` | *(absent)* | `["concomitant_medications", "medical_history", "creatine_kinase_level"]` | *(new array)* |

---

### Effect at Runtime

After this change, on every service startup:

```
INFO  CaseStore - Bootstrapped case 'PV-2026-0451' (version 1)
INFO  CaseStore - Applied follow-up 'followup_report_PV-2026-0451-FU1.pdf'
                  → case 'PV-2026-0451' advanced to version 2
                  (overridden fields: weight_kg, dose, event_term, outcome, seriousness;
                   new fields: hospitalization;
                   missing_fields: [concomitant_medications, medical_history, creatine_kinase_level])
```

`GET /cases/PV-2026-0451` returns the annotated version-2 merged case immediately —
no manual POST required before the Phase 2 session.
