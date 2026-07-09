package com.theragenx.icsr.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theragenx.icsr.model.Case;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory case store. Replaces a database for this exercise.
 * Bootstraps from case_v1.json on startup.
 */
@Component
public class CaseStore {

    private final ConcurrentHashMap<String, Case> cases = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CaseStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // TODO: load case_v1.json from classpath and put into `cases`
        // Hint: objectMapper.readValue(new ClassPathResource("case_v1.json").getInputStream(), Case.class)
    }

    public Optional<Case> findById(String caseId) {
        // TODO: return Optional.ofNullable(cases.get(caseId))
        throw new UnsupportedOperationException("TODO: findById");
    }

    public void save(Case c) {
        // TODO: cases.put(c.caseId(), c)
        throw new UnsupportedOperationException("TODO: save");
    }
}
