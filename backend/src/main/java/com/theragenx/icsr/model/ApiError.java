package com.theragenx.icsr.model;

import java.time.Instant;

/**
 * Uniform error response body returned for all 4xx/5xx responses.
 *
 * <pre>
 * {
 *   "status":    404,
 *   "error":     "NOT_FOUND",
 *   "message":   "Case not found: PV-2026-0451",
 *   "path":      "/cases/PV-2026-0451",
 *   "timestamp": "2026-07-09T14:05:00Z"
 * }
 * </pre>
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {}
