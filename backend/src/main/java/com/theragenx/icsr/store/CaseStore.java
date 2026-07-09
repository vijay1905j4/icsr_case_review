package com.theragenx.icsr.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theragenx.icsr.model.Case;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class CaseStore {

    private static final Logger log = LoggerFactory.getLogger(CaseStore.class);

    private final ConcurrentHashMap<String, Case> cases = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CaseStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Seeds the store from {@code case_v1.json} on the classpath.
     * Fails fast at startup if the file is missing or malformed.
     */
    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource("case_v1.json");
            Case bootstrapCase = objectMapper.readValue(resource.getInputStream(), Case.class);
            cases.put(bootstrapCase.caseId(), bootstrapCase);
            log.info("Bootstrapped case '{}' (version {})", bootstrapCase.caseId(), bootstrapCase.version());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load bootstrap case from classpath:case_v1.json — cannot start", e);
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
}
