package com.theragenx.icsr.model;

import java.time.Instant;

/**
 * A reviewer query raised against a specific field in a case.
 * <p>
 * queryId   — server-assigned UUID string
 * fieldPath — dot-separated path, e.g. "adverse_event.onset_date"
 * createdAt — UTC timestamp assigned at creation; serialised as ISO-8601
 */
public record Query(
        String queryId,
        String caseId,
        String fieldPath,
        String question,
        Instant createdAt
) {}
