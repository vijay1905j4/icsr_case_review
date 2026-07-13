package com.theragenx.icsr.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.service.MergeService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory case store keyed by caseId.
 * <p>
 * Only the <em>latest</em> version of each case is kept. When a follow-up is
 * merged the resulting Case (with an incremented version number) replaces the
 * old entry via {@link #save}. The diff annotations on the merge response are
 * the audit trail — no history stack is maintained.
 * <p>
 * If {@code icsr.bootstrap.apply-followup=true} (the default), the store also
 * applies {@code case_v2_followup_payload.json} through {@link MergeService}
 * during startup, so {@code GET /cases/PV-2026-0451} immediately returns the
 * annotated version-2 merged case without requiring a manual POST.
 */
@Component
public class CaseStore {

    private static final Logger log = LoggerFactory.getLogger(CaseStore.class);

    private final ConcurrentHashMap<String, Case> cases = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MergeService mergeService;

    /** When true, the v2 follow-up is applied automatically on startup. */
    @Value("${icsr.bootstrap.apply-followup:true}")
    private boolean applyFollowUp;

    public CaseStore(ObjectMapper objectMapper, MergeService mergeService) {
        this.objectMapper = objectMapper;
        this.mergeService = mergeService;
    }

    /**
     * Seeds the store from {@code case_v1.json} on the classpath, then
     * optionally applies {@code case_v2_followup_payload.json} through
     * {@link MergeService} to produce the annotated merged case.
     * Fails fast at startup if any required file is missing or malformed.
     */
    @PostConstruct
    public void init() {
        // --- Step 1: load the initial v1 case ---
        Case bootstrapCase = loadCaseFromClasspath("case_v1.json");
        cases.put(bootstrapCase.caseId(), bootstrapCase);
        log.info("Bootstrapped case '{}' (version {})", bootstrapCase.caseId(), bootstrapCase.version());

        // --- Step 2 (optional): apply the v2 follow-up so the merged case is
        //     immediately queryable without a manual POST. ---
        if (applyFollowUp) {
            Case followUp = loadCaseFromClasspath("case_v2_followup_payload.json");
            Case merged = mergeService.merge(bootstrapCase, followUp);
            cases.put(merged.caseId(), merged);
            log.info(
                "Applied follow-up '{}' → case '{}' advanced to version {} " +
                "(overridden fields: weight_kg, dose, event_term, outcome, seriousness; " +
                "new fields: hospitalization; missing_fields: {})",
                followUp.sourceDocument(),
                merged.caseId(),
                merged.version(),
                merged.missingFields()
            );
        }
    }

    /** Returns the latest version of the case, or empty if not found. */
    public Optional<Case> findById(String caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    /** Persists (or replaces) a case. Used after each successful follow-up merge. */
    public void save(Case c) {
        cases.put(c.caseId(), c);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Loads and deserialises a {@link Case} from the given classpath resource name.
     * Throws {@link IllegalStateException} on any IO or parse error so startup
     * fails fast with a clear message.
     */
    private Case loadCaseFromClasspath(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            return objectMapper.readValue(resource.getInputStream(), Case.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load case from classpath:" + resourceName + " — cannot start", e);
        }
    }
}
