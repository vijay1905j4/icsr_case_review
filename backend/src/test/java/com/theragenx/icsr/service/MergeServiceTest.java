package com.theragenx.icsr.service;

import com.theragenx.icsr.model.AnnotatedField;
import com.theragenx.icsr.model.Case;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MergeService — no Spring context needed.
 * Covers all four merge statuses defined in DESIGN.md, plus version increment
 * and missing_fields propagation.
 */
class MergeServiceTest {

    private MergeService mergeService;

    @BeforeEach
    void setUp() {
        mergeService = new MergeService();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds a minimal AnnotatedField (no merge annotation — simulates stored data). */
    private static AnnotatedField field(String value, double confidence) {
        return new AnnotatedField(value, confidence, "p.1 §1", null);
    }

    /** Builds a Case containing exactly one section with one field. */
    private static Case caseWith(String section, String fieldKey, String value) {
        return new Case(
                "PV-2026-0451", 1, "non-significant",
                "2026-04-08T09:14:00Z", "report.pdf",
                Map.of(section, Map.of(fieldKey, field(value, 0.90))),
                null
        );
    }

    /** Builds an empty Case (no sections, no missing_fields). */
    private static Case emptyCaseFollowUp() {
        return new Case(
                "PV-2026-0451", 2, "non-significant",
                "2026-04-15T11:00:00Z", "followup.pdf",
                Map.of(), null
        );
    }

    // ------------------------------------------------------------------
    // Merge status tests
    // ------------------------------------------------------------------

    @Test
    void sameValue_producesUnchangedStatus() {
        Case stored   = caseWith("patient", "age", "62");
        Case followUp = caseWith("patient", "age", "62");

        Case merged = mergeService.merge(stored, followUp);

        AnnotatedField f = merged.sections().get("patient").get("age");
        assertEquals("unchanged", f.merge().status());
        assertNull(f.merge().previousValue(), "unchanged fields must not carry a previous_value");
    }

    @Test
    void differentValue_producesOverriddenStatus_withPreviousValue() {
        Case stored   = caseWith("patient", "age", "62");
        Case followUp = caseWith("patient", "age", "63");

        Case merged = mergeService.merge(stored, followUp);

        AnnotatedField f = merged.sections().get("patient").get("age");
        assertEquals("overridden",  f.merge().status());
        assertEquals("62",          f.merge().previousValue());
        assertEquals("63",          f.value(), "merged field should carry the follow-up value");
    }

    @Test
    void fieldAbsentFromFollowUp_isRetained_withStoredValue() {
        Case stored   = caseWith("patient", "weight_kg", "78");
        Case followUp = emptyCaseFollowUp(); // no patient section at all

        Case merged = mergeService.merge(stored, followUp);

        AnnotatedField f = merged.sections().get("patient").get("weight_kg");
        assertNotNull(f, "retained field must still appear in the merged response");
        assertEquals("retained", f.merge().status());
        assertEquals("78",       f.value(), "stored value must be preserved");
        assertNull(f.merge().previousValue(), "retained fields have no previous_value");
    }

    @Test
    void fieldPresentOnlyInFollowUp_hasNewStatus() {
        Case stored   = caseWith("patient", "age", "62");
        Case followUp = caseWith("adverse_event", "hospitalization", "Yes");

        Case merged = mergeService.merge(stored, followUp);

        AnnotatedField f = merged.sections().get("adverse_event").get("hospitalization");
        assertNotNull(f, "new field must appear in the merged response");
        assertEquals("new", f.merge().status());
        assertNull(f.merge().previousValue());
    }

    // ------------------------------------------------------------------
    // Structural / metadata tests
    // ------------------------------------------------------------------

    @Test
    void mergedCase_hasVersionIncrementedByOne() {
        Case stored   = caseWith("patient", "age", "62");
        Case followUp = caseWith("patient", "age", "62");

        Case merged = mergeService.merge(stored, followUp);

        assertEquals(2, merged.version(), "version must be stored.version + 1");
    }

    @Test
    void missingFields_fromFollowUp_areSurfacedOnMergedCase() {
        Case stored = caseWith("patient", "age", "62");
        Case followUp = new Case(
                "PV-2026-0451", 2, "non-significant",
                "2026-04-15T11:00:00Z", "followup.pdf",
                Map.of("patient", Map.of("age", field("62", 0.95))),
                List.of("patient.weight_kg", "reporter.country")
        );

        Case merged = mergeService.merge(stored, followUp);

        assertNotNull(merged.missingFields());
        assertEquals(List.of("patient.weight_kg", "reporter.country"), merged.missingFields());
    }
}
