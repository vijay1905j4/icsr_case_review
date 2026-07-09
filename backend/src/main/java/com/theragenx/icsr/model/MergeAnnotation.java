package com.theragenx.icsr.model;

/**
 * Merge annotation attached to every field in a merged-case response.
 * <p>
 * status values: "unchanged" | "overridden" | "new" | "retained"
 * previousValue is only set when status == "overridden".
 * Both fields are serialised as snake_case by global Jackson config.
 * Null fields are omitted from JSON output (NON_NULL global config).
 */
public record MergeAnnotation(
        String status,
        String previousValue   // null unless status == "overridden"
) {
    /** Convenience factories so callers never construct raw strings at call sites. */
    public static MergeAnnotation unchanged() {
        return new MergeAnnotation("unchanged", null);
    }

    public static MergeAnnotation overridden(String previousValue) {
        return new MergeAnnotation("overridden", previousValue);
    }

    public static MergeAnnotation newField() {
        return new MergeAnnotation("new", null);
    }

    public static MergeAnnotation retained() {
        return new MergeAnnotation("retained", null);
    }
}
