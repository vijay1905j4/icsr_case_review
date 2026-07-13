package com.theragenx.icsr.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.service.MergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit test — no Spring context.
 *
 * <p>Tests cover two scenarios:
 * <ol>
 *   <li>Default boot (applyFollowUp=true): store holds the v2 merged case.</li>
 *   <li>v1-only boot (applyFollowUp=false): store holds the raw bootstrapped v1 case.</li>
 * </ol>
 */
class CaseStoreTest {

    private static final String CASE_ID = "PV-2026-0451";

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        // Mirror the Jackson config from application.properties
        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Helper: create a store and trigger init() with the given applyFollowUp flag. */
    private CaseStore buildStore(boolean applyFollowUp) throws Exception {
        MergeService mergeService = new MergeService();
        CaseStore store = new CaseStore(mapper, mergeService);

        // Inject the @Value field reflectively (no Spring context in this test)
        var field = CaseStore.class.getDeclaredField("applyFollowUp");
        field.setAccessible(true);
        field.set(store, applyFollowUp);

        store.init();
        return store;
    }

    // -------------------------------------------------------------------------
    // v1-only mode (applyFollowUp = false)
    // -------------------------------------------------------------------------

    @Test
    void v1OnlyBoot_caseIsPresent() throws Exception {
        CaseStore store = buildStore(false);
        Optional<Case> found = store.findById(CASE_ID);
        assertTrue(found.isPresent(), "Bootstrap case should be loaded on startup");
    }

    @Test
    void v1OnlyBoot_hasVersionOne() throws Exception {
        CaseStore store = buildStore(false);
        Case c = store.findById(CASE_ID).orElseThrow();
        assertEquals(1, c.version(), "v1-only boot should yield version 1");
        assertEquals("non-significant", c.caseClassification());
    }

    @Test
    void v1OnlyBoot_patientSectionDeserialised() throws Exception {
        CaseStore store = buildStore(false);
        Case c = store.findById(CASE_ID).orElseThrow();
        var patient = c.sections().get("patient");

        assertNotNull(patient, "patient section should exist");
        assertEquals("M.K.",  patient.get("initials").value());
        assertEquals("62",    patient.get("age").value());
        assertEquals(0.99,    patient.get("sex").confidence(), 0.001);
    }

    @Test
    void unknownCaseId_returnsEmpty() throws Exception {
        CaseStore store = buildStore(false);
        assertTrue(store.findById("DOES-NOT-EXIST").isEmpty(),
                "Unknown caseId should return Optional.empty()");
    }

    // -------------------------------------------------------------------------
    // v2 merged boot (applyFollowUp = true — the default)
    // -------------------------------------------------------------------------

    @Test
    void v2MergedBoot_caseIsVersion2() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();
        assertEquals(2, c.version(), "After applying follow-up, case should be version 2");
    }

    @Test
    void v2MergedBoot_doseIsOverridden() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();
        var dose = c.sections().get("suspect_drug").get("dose");

        assertEquals("40 mg", dose.value(), "Follow-up dose (40 mg) should win");
        assertNotNull(dose.merge(), "Merged field must carry a merge annotation");
        assertEquals("overridden", dose.merge().status());
        assertEquals("20 mg", dose.merge().previousValue(), "Previous dose was 20 mg");
    }

    @Test
    void v2MergedBoot_hospitalizationIsNew() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();
        var hosp = c.sections().get("adverse_event").get("hospitalization");

        assertNotNull(hosp, "hospitalization should appear after follow-up");
        assertEquals("Yes - 5 days", hosp.value());
        assertEquals("new", hosp.merge().status());
    }

    @Test
    void v2MergedBoot_missingFieldsPopulated() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();

        assertNotNull(c.missingFields(), "missing_fields should be present after follow-up");
        assertTrue(c.missingFields().contains("concomitant_medications"));
        assertTrue(c.missingFields().contains("medical_history"));
        assertTrue(c.missingFields().contains("creatine_kinase_level"));
    }

    @Test
    void v2MergedBoot_unchangedFieldHasNoAnnotationStatus() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();
        var initials = c.sections().get("patient").get("initials");

        assertEquals("M.K.", initials.value());
        assertEquals("unchanged", initials.merge().status(),
                "initials is the same in both versions — should be unchanged");
        assertNull(initials.merge().previousValue(), "unchanged fields carry no previousValue");
    }

    @Test
    void v2MergedBoot_eventTermIsOverridden() throws Exception {
        CaseStore store = buildStore(true);
        Case c = store.findById(CASE_ID).orElseThrow();
        var eventTerm = c.sections().get("adverse_event").get("event_term");

        assertEquals("Rhabdomyolysis", eventTerm.value());
        assertEquals("overridden", eventTerm.merge().status());
        assertEquals("Myalgia", eventTerm.merge().previousValue());
    }
}
