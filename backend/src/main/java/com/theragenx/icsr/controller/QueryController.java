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
 * POST /queries              → create a new query
 * GET  /queries?caseId={id} → list queries for a case
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
     * 400 if body fails validation. 404 if caseId does not exist.
     */
    @PostMapping
    public ResponseEntity<Query> createQuery(@RequestBody @Valid CreateQueryRequest request) {
        // TODO: Query created = queryService.createQuery(request);
        //       return ResponseEntity.status(HttpStatus.CREATED).body(created);
        throw new UnsupportedOperationException("TODO: createQuery");
    }

    /**
     * Lists all queries for the given case, in creation order.
     * 404 if caseId does not exist.
     */
    @GetMapping
    public ResponseEntity<List<Query>> listQueries(@RequestParam String caseId) {
        // TODO: return ResponseEntity.ok(queryService.listQueries(caseId));
        throw new UnsupportedOperationException("TODO: listQueries");
    }
}
