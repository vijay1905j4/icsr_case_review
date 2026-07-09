package com.theragenx.icsr.model;

import java.util.List;
import java.util.Map;

/**
 * Top-level case document — used for both stored cases and merged responses.
 * <p>
 * sections: sectionKey -> (fieldKey -> AnnotatedField)
 * missingFields: list of dot-separated field paths the AI could not extract
 *                (e.g. "patient.weight_kg"). Null for the bootstrapped v1 case.
 *
 * Jackson serialises field names as snake_case via global config
 * (caseId -> case_id, caseClassification -> case_classification, etc.).
 * Null fields (missingFields when absent) are omitted from JSON output.
 */
public record Case(
        String caseId,
        int version,
        String caseClassification,
        String extractedAt,
        String sourceDocument,
        Map<String, Map<String, AnnotatedField>> sections,
        List<String> missingFields   // null for stored v1; populated by follow-up payloads
) {}
