package com.theragenx.icsr.controller;

import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.service.MergeService;
import com.theragenx.icsr.store.CaseStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles case retrieval and follow-up merging.
 *
 * GET  /cases/{caseId}            → return stored case
 * POST /cases/{caseId}/follow-ups → merge follow-up, return annotated case
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
     * Returns the most recent stored version of a case.
     * 404 if caseId is not found.
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<Case> getCase(@PathVariable String caseId) {
        // TODO: caseStore.findById(caseId).map(ResponseEntity::ok).orElseThrow(CaseNotFoundException)
        throw new UnsupportedOperationException("TODO: getCase");
    }

    /**
     * Accepts a follow-up payload, merges it onto the stored case,
     * persists the merged result, and returns the annotated merged case.
     * 400 if body is malformed. 404 if caseId is not found.
     */
    @PostMapping("/{caseId}/follow-ups")
    public ResponseEntity<Case> postFollowUp(
            @PathVariable String caseId,
            @RequestBody Case followUp) {
        // TODO: validate caseId matches followUp.caseId()
        //       fetch stored = caseStore.findById(caseId)
        //       merged = mergeService.merge(stored, followUp)
        //       caseStore.save(merged)
        //       return ResponseEntity.ok(merged)
        throw new UnsupportedOperationException("TODO: postFollowUp");
    }
}
