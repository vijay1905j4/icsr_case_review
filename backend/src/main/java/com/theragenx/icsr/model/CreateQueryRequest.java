package com.theragenx.icsr.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /queries.
 * All three fields are required and validated before the service layer is called.
 */
public record CreateQueryRequest(

        @NotBlank(message = "case_id must not be blank")
        String caseId,

        @NotBlank(message = "field_path must not be blank")
        String fieldPath,

        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be 2000 characters or fewer")
        String question
) {}
