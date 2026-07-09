package com.theragenx.icsr.controller;

import com.theragenx.icsr.model.CreateQueryRequest;
import com.theragenx.icsr.model.Query;
import com.theragenx.icsr.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles reviewer query creation and listing.
 *
 * <pre>
 * POST /queries              → create a query (body: {caseId, fieldPath, question})
 * GET  /queries?caseId={id} → list all queries for the given case
 * </pre>
 */
@RestController
@RequestMapping("/queries")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Creates a reviewer query against a specific field.
     * Returns 201 Created with the persisted query (including server-assigned id and timestamp).
     *
     * <p>Validations (via {@link Valid} + {@link GlobalExceptionHandler}):
     * <ul>
     *   <li>400 — caseId, fieldPath, or question blank or missing</li>
     *   <li>404 — caseId does not exist</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Query> createQuery(@RequestBody @Valid CreateQueryRequest request) {
        Query created = queryService.createQuery(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all queries for the given case in creation order.
     *
     * <p>Validations:
     * <ul>
     *   <li>400 — {@code caseId} param is missing entirely</li>
     *   <li>404 — caseId does not exist in the store</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<Query>> listQueries(@RequestParam String caseId) {
        return ResponseEntity.ok(queryService.listQueries(caseId));
    }
}

