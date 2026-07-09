package com.theragenx.icsr.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.theragenx.icsr.model.Case;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit test — no Spring context.
 * Verifies that CaseStore.init() correctly deserialises case_v1.json
 * and makes the case queryable via findById.
 */
class CaseStoreTest {

    private CaseStore store;

    @BeforeEach
    void setUp() {
        // Mirror the Jackson config from application.properties
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        store = new CaseStore(mapper);
        store.init(); // triggers classpath:case_v1.json load
    }

    @Test
    void bootstrapCase_isPresent() {
        Optional<Case> found = store.findById("PV-2026-0451");
        assertTrue(found.isPresent(), "Bootstrap case PV-2026-0451 should be loaded on startup");
    }

    @Test
    void bootstrapCase_hasCorrectVersionAndClassification() {
        Case c = store.findById("PV-2026-0451").orElseThrow();
        assertEquals(1, c.version(), "Bootstrap case should be version 1");
        assertEquals("non-significant", c.caseClassification());
    }

    @Test
    void bootstrapCase_patientSectionDeserialised() {
        Case c = store.findById("PV-2026-0451").orElseThrow();
        var patient = c.sections().get("patient");

        assertNotNull(patient, "patient section should exist");
        assertEquals("M.K.", patient.get("initials").value());
        assertEquals("62",   patient.get("age").value());
        assertEquals(0.99,   patient.get("sex").confidence(), 0.001);
    }

    @Test
    void unknownCaseId_returnsEmpty() {
        assertTrue(store.findById("DOES-NOT-EXIST").isEmpty(),
                "Unknown caseId should return Optional.empty()");
    }
}
