package com.theragenx.icsr.store;

import com.theragenx.icsr.model.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory query store. Keyed by caseId; each case may have many queries.
 * Queries are append-only and returned in insertion order.
 */
@Component
public class QueryStore {

    // caseId → ordered list of queries (list itself is synchronised)
    private final ConcurrentHashMap<String, List<Query>> queries = new ConcurrentHashMap<>();

    /** Appends a query to the list for its case. Thread-safe via synchronised list. */
    public void save(Query query) {
        queries.computeIfAbsent(
                query.caseId(),
                k -> Collections.synchronizedList(new ArrayList<>())
        ).add(query);
    }

    /** Returns all queries for the given case in insertion order. Never null. */
    public List<Query> findByCaseId(String caseId) {
        List<Query> found = queries.get(caseId);
        if (found == null) {
            return List.of();
        }
        // Snapshot under the list's own lock to avoid ConcurrentModificationException.
        synchronized (found) {
            return List.copyOf(found);
        }
    }
}

