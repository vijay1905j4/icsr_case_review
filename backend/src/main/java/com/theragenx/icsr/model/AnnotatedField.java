package com.theragenx.icsr.model;

/**
 * A single extracted field, optionally carrying a merge annotation.
 * <p>
 * When returned from GET /cases/{id} (stored case), {@code merge} is null
 * and is omitted from JSON (NON_NULL global config).
 * When returned from POST /cases/{id}/follow-ups (merged response),
 * {@code merge} is always present.
 */
public record AnnotatedField(
        String value,
        double confidence,
        String source,
        MergeAnnotation merge   // null for stored/bootstrapped cases; always set on merged responses
) {}
