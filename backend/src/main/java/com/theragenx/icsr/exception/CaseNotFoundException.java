package com.theragenx.icsr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested caseId is not found in the store.
 * @ResponseStatus maps it to a 404 without a global exception handler.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String caseId) {
        super("Case not found: " + caseId);
    }
}
