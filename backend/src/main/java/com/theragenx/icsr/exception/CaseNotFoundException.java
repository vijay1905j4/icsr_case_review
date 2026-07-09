package com.theragenx.icsr.exception;

/**
 * Thrown when a requested caseId is not found in the store.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String caseId) {
        super("Case not found: " + caseId);
    }
}
