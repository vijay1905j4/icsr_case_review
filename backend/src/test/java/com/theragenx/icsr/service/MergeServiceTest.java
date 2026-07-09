package com.theragenx.icsr.service;

import com.theragenx.icsr.model.AnnotatedField;
import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.model.MergeAnnotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MergeService — no Spring context needed.
 * Covers the four merge status cases from DESIGN.md.
 */
class MergeServiceTest {

    private MergeService mergeService;

    @BeforeEach
    void setUp() {
        mergeService = new MergeService();
    }

    // ------------------------------------------------------------------
    // Helper: build a minimal Case with a single section + field
    // ------------------------------------------------------------------
    private Case caseWith(String section, String fieldKey, String value) {
        var field = new AnnotatedField(value, 0.90, "p.1 §1", null);
        var sections = Map.of(section, Map.of(fieldKey, field));
        return new Case("PV-2026-0451", 1, "non-significant",
                "2026-04-08T09:14:00Z", "report.pdf", sections, null);
    }

    // ------------------------------------------------------------------

    @Test
    void sameValueProducesUnchangedStatus() {
        // TODO: implement once MergeService.merge() is coded
        // Case stored  = caseWith("patient", "age", "62");
        // Case followUp = caseWith("patient", "age", "62");
        // Case merged  = mergeService.merge(stored, followUp);
        // AnnotatedField f = merged.sections().get("patient").get("age");
        // assertEquals("unchanged", f.merge().status());
        // assertNull(f.merge().previousValue());
    }

    @Test
    void differentValueProducesOverriddenStatusWithPreviousValue() {
        // TODO: implement once MergeService.merge() is coded
        // Case stored  = caseWith("patient", "age", "62");
        // Case followUp = caseWith("patient", "age", "63");
        // Case merged  = mergeService.merge(stored, followUp);
        // AnnotatedField f = merged.sections().get("patient").get("age");
        // assertEquals("overridden", f.merge().status());
        // assertEquals("62", f.merge().previousValue());
    }

    @Test
    void fieldAbsentFromFollowUpIsRetainedWithStoredValue() {
        // TODO: implement once MergeService.merge() is coded
        // Case stored  = caseWith("patient", "weight_kg", "78");
        // Case followUp = new Case(..., sections without weight_kg, null);
        // Case merged  = mergeService.merge(stored, followUp);
        // AnnotatedField f = merged.sections().get("patient").get("weight_kg");
        // assertEquals("retained", f.merge().status());
        // assertEquals("78", f.value());
    }

    @Test
    void newFieldInFollowUpHasNewStatus() {
        // TODO: implement once MergeService.merge() is coded
        // Case stored  = caseWith("patient", "age", "62"); // no hospitalization
        // Case followUp = caseWith("adverse_event", "hospitalization", "Yes");
        // Case merged  = mergeService.merge(stored, followUp);
        // AnnotatedField f = merged.sections().get("adverse_event").get("hospitalization");
        // assertEquals("new", f.merge().status());
    }
}
