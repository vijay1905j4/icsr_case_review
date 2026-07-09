package com.theragenx.icsr.service;

import com.theragenx.icsr.model.AnnotatedField;
import com.theragenx.icsr.model.Case;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MergeService}.
 *
 * Covers the five merge rules defined in DESIGN.md:
 *   (a) Same value in both versions       → "unchanged"
 *   (b) Different value in follow-up      → "overridden" + previous_value
 *   (c) Field only in follow-up           → "new"
 *   (d) Field only in stored version      → "retained" (not dropped)
 *   (e) Follow-up's missing_fields list   → surfaced on merged response
 *
 * No Spring context is loaded. MergeService has no external dependencies,
 * so it can be constructed directly.
 */
class MergeServiceTest {

    private final MergeService mergeService = new MergeService();

    // =========================================================================
    // (a) UNCHANGED — AI re-extracted the field and the value has not changed
    // =========================================================================

    @Test
    @DisplayName("(a) Same value in stored and follow-up → field is annotated 'unchanged', no previous_value")
    void case_a_sameValue_isAnnotatedUnchanged() {

        // Arrange
        Case stored   = caseWithOneField("patient", "age", "62");
        Case followUp = caseWithOneField("patient", "age", "62"); // identical value

        // Act
        Case merged = mergeService.merge(stored, followUp);

        // Assert
        AnnotatedField age = merged.sections().get("patient").get("age");

        assertNotNull(age.merge(),
                "Every field in the merged response must carry a merge annotation.");

        assertEquals("unchanged", age.merge().status(),
                "A field whose value did not change must be marked 'unchanged'.");

        assertNull(age.merge().previousValue(),
                "An unchanged field has no previous value to record — previousValue must be null.");

        assertEquals("62", age.value(),
                "The unchanged field must still carry its current value.");
    }

    // =========================================================================
    // (b) OVERRIDDEN — AI re-extracted the field and the value has changed
    // =========================================================================

    @Test
    @DisplayName("(b) Different value in follow-up → field is 'overridden', previous_value holds the old value")
    void case_b_differentValue_isAnnotatedOverridden_withPreviousValue() {

        // Arrange
        Case stored   = caseWithOneField("patient", "age", "62"); // old value
        Case followUp = caseWithOneField("patient", "age", "63"); // updated value

        // Act
        Case merged = mergeService.merge(stored, followUp);

        // Assert
        AnnotatedField age = merged.sections().get("patient").get("age");

        assertEquals("overridden", age.merge().status(),
                "A field whose value changed must be marked 'overridden'.");

        assertEquals("62", age.merge().previousValue(),
                "previous_value must hold the value from the stored version so the reviewer can see what changed.");

        assertEquals("63", age.value(),
                "The merged field must carry the follow-up's newer value, not the stored value.");
    }

    // =========================================================================
    // (c) NEW — field appears in the follow-up but did not exist in stored
    // =========================================================================

    @Test
    @DisplayName("(c) Field present only in follow-up → field is annotated 'new', no previous_value")
    void case_c_fieldOnlyInFollowUp_isAnnotatedNew() {

        // Arrange — stored case has no adverse_event section whatsoever
        Case stored   = caseWithOneField("patient", "age", "62");
        Case followUp = caseWithOneField("adverse_event", "hospitalization", "Yes");

        // Act
        Case merged = mergeService.merge(stored, followUp);

        // Assert
        AnnotatedField hospitalization =
                merged.sections().get("adverse_event").get("hospitalization");

        assertNotNull(hospitalization,
                "A new field must appear in the merged response — it must not be dropped.");

        assertEquals("new", hospitalization.merge().status(),
                "A field that did not exist in the stored version must be marked 'new'.");

        assertNull(hospitalization.merge().previousValue(),
                "A new field has no prior value, so previousValue must be null.");
    }

    // =========================================================================
    // (d) RETAINED — field exists in stored but the AI did not re-extract it
    //
    // Decision (from DESIGN.md): preserve the stored value and mark it
    // 'retained'. Do NOT drop the field. A follow-up is a partial re-extraction,
    // not a replacement — silently losing a field would destroy information.
    // =========================================================================

    @Test
    @DisplayName("(d) Field in stored but absent from follow-up → field is 'retained', stored value is preserved, field is not dropped")
    void case_d_fieldAbsentFromFollowUp_isAnnotatedRetained() {

        // Arrange — follow-up has no patient section; the AI did not revisit it
        Case stored = caseWithOneField("patient", "weight_kg", "78");

        Case followUp = new Case(
                "PV-2026-0451",
                2,
                "non-significant",
                "2026-04-15T11:00:00Z",
                "followup.pdf",
                Map.of(),   // no sections — weight_kg is entirely absent
                null
        );

        // Act
        Case merged = mergeService.merge(stored, followUp);

        // Assert
        AnnotatedField weightKg = merged.sections().get("patient").get("weight_kg");

        assertNotNull(weightKg,
                "A retained field must still appear in the merged response — it must not be silently dropped.");

        assertEquals("retained", weightKg.merge().status(),
                "A field the AI did not re-extract should be marked 'retained', not dropped or left unannotated.");

        assertEquals("78", weightKg.value(),
                "The retained field must preserve the value from the stored version.");

        assertNull(weightKg.merge().previousValue(),
                "A retained field has not been overridden — previousValue must be null.");
    }

    // =========================================================================
    // (e) MISSING_FIELDS — the follow-up's AI-reported list is surfaced
    //
    // missing_fields is an annotation for the current extraction run only.
    // It replaces (not accumulates) any previous missing_fields.
    // =========================================================================

    @Test
    @DisplayName("(e) follow-up's missing_fields list is surfaced verbatim on the merged response")
    void case_e_missingFields_fromFollowUp_areSurfacedOnMergedCase() {

        // Arrange
        Case stored = caseWithOneField("patient", "age", "62");

        // The follow-up AI could not extract these two fields
        List<String> aiCouldNotExtract = List.of("patient.weight_kg", "reporter.country");

        Case followUp = new Case(
                "PV-2026-0451",
                2,
                "non-significant",
                "2026-04-15T11:00:00Z",
                "followup.pdf",
                Map.of("patient", Map.of("age", storedField("62"))),
                aiCouldNotExtract
        );

        // Act
        Case merged = mergeService.merge(stored, followUp);

        // Assert
        assertNotNull(merged.missingFields(),
                "The merged response must include the missing_fields list from the follow-up.");

        assertEquals(
                List.of("patient.weight_kg", "reporter.country"),
                merged.missingFields(),
                "missing_fields must be the follow-up's list, copied verbatim — not null, not empty, not merged with stored missing_fields."
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a minimal {@link AnnotatedField} with no merge annotation.
     * This represents a field as it would exist in a stored (bootstrapped) case.
     */
    private static AnnotatedField storedField(String value) {
        return new AnnotatedField(value, 0.90, "p.1 §1", null);
    }

    /**
     * Creates a minimal {@link Case} containing exactly one section with one field.
     * All non-essential metadata uses fixed placeholder values so each test stays
     * focused on the specific field behaviour it is verifying.
     */
    private static Case caseWithOneField(String sectionKey, String fieldKey, String value) {
        return new Case(
                "PV-2026-0451",
                1,
                "non-significant",
                "2026-04-08T09:14:00Z",
                "report.pdf",
                Map.of(sectionKey, Map.of(fieldKey, storedField(value))),
                null
        );
    }
}
