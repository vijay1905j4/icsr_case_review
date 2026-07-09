package com.theragenx.icsr.controller;

import com.theragenx.icsr.exception.CaseNotFoundException;
import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.service.MergeService;
import com.theragenx.icsr.store.CaseStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles case retrieval and follow-up merging.
 *
 * <pre>
 * GET  /cases/{caseId}            → return the latest stored case
 * POST /cases/{caseId}/follow-ups → merge follow-up, return annotated merged case
 * </pre>
 */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseStore caseStore;
    private final MergeService mergeService;

    public CaseController(CaseStore caseStore, MergeService mergeService) {
        this.caseStore = caseStore;
        this.mergeService = mergeService;
    }

    /**
     * Returns the most recent stored version of the case.
     * Responds 404 if the caseId is not found.
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<Case> getCase(@PathVariable String caseId) {
        Case c = caseStore.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        return ResponseEntity.ok(c);
    }

    /**
     * Merges the follow-up payload onto the stored case, persists the result,
     * and returns the fully annotated merged case.
     *
     * <p>Validations:
     * <ul>
     *   <li>400 — body caseId must match the URL path caseId</li>
     *   <li>400 — body must include a non-null {@code sections} map</li>
     *   <li>404 — caseId must exist in the store</li>
     * </ul>
     */
    @PostMapping("/{caseId}/follow-ups")
    public ResponseEntity<Case> postFollowUp(
            @PathVariable String caseId,
            @RequestBody Case followUp) {

        // Validate: URL caseId must match body case_id.
        if (followUp.caseId() == null || !caseId.equals(followUp.caseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL caseId '" + caseId + "' does not match body case_id '"
                            + followUp.caseId() + "'");
        }

        // Validate: sections must be present (can be empty, but not null).
        if (followUp.sections() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Follow-up payload must include a 'sections' field");
        }

        Case stored = caseStore.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        Case merged = mergeService.merge(stored, followUp);
        caseStore.save(merged);

        return ResponseEntity.ok(merged);
    }
}

