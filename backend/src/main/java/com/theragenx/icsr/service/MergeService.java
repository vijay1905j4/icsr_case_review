package com.theragenx.icsr.service;

import com.theragenx.icsr.model.Case;
import org.springframework.stereotype.Service;

/**
 * Merge service: applies a follow-up case onto the stored version,
 * producing a fully annotated merged response.
 *
 * <p>Merge rules (per DESIGN.md):
 * <ul>
 *   <li>Same value     → status: "unchanged"</li>
 *   <li>Different value → status: "overridden", previousValue set</li>
 *   <li>New field       → status: "new"</li>
 *   <li>Field absent from follow-up → status: "retained" (stored value preserved)</li>
 * </ul>
 */
@Service
public class MergeService {

    /**
     * Merges {@code followUp} onto {@code stored} and returns an annotated Case.
     *
     * @param stored   the current stored version of the case
     * @param followUp the inbound follow-up payload
     * @return a new Case with version incremented and all fields annotated
     */
    public Case merge(Case stored, Case followUp) {
        // TODO: iterate stored.sections() and followUp.sections()
        //       for each field apply the four-way merge status logic
        //       carry followUp.missingFields() into the merged response
        throw new UnsupportedOperationException("TODO: merge logic");
    }
}
