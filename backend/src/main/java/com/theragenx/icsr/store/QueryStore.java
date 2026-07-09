package com.theragenx.icsr.store;

import com.theragenx.icsr.model.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory query store. Keyed by caseId; each case may have many queries.
 */
@Component
public class QueryStore {

    // caseId -> ordered list of queries
    private final ConcurrentHashMap<String, List<Query>> queries = new ConcurrentHashMap<>();

    public void save(Query query) {
        // TODO: queries.computeIfAbsent(query.caseId(), k -> new ArrayList<>()).add(query)
        throw new UnsupportedOperationException("TODO: save");
    }

    public List<Query> findByCaseId(String caseId) {
        // TODO: return Collections.unmodifiableList(queries.getOrDefault(caseId, List.of()))
        throw new UnsupportedOperationException("TODO: findByCaseId");
    }
}
