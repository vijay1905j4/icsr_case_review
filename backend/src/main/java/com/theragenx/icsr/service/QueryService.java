package com.theragenx.icsr.service;

import com.theragenx.icsr.exception.CaseNotFoundException;
import com.theragenx.icsr.model.CreateQueryRequest;
import com.theragenx.icsr.model.Query;
import com.theragenx.icsr.store.CaseStore;
import com.theragenx.icsr.store.QueryStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query service: creates and retrieves reviewer queries.
 * Always verifies the target case exists before reading or writing queries.
 */
@Service
public class QueryService {

    private final QueryStore queryStore;
    private final CaseStore caseStore;

    public QueryService(QueryStore queryStore, CaseStore caseStore) {
        this.queryStore = queryStore;
        this.caseStore = caseStore;
    }

    /**
     * Creates a new query, assigning a UUID and UTC timestamp.
     *
     * @throws CaseNotFoundException if the caseId does not exist in the store
     */
    public Query createQuery(CreateQueryRequest request) {
        // Guard: reject queries against non-existent cases.
        caseStore.findById(request.caseId())
                .orElseThrow(() -> new CaseNotFoundException(request.caseId()));

        Query query = new Query(
                UUID.randomUUID().toString(),
                request.caseId(),
                request.fieldPath(),
                request.question(),
                Instant.now()
        );

        queryStore.save(query);
        return query;
    }

    /**
     * Returns all queries for the given case in creation order.
     *
     * @throws CaseNotFoundException if the caseId does not exist in the store
     */
    public List<Query> listQueries(String caseId) {
        // Guard: distinguish "case exists with no queries" from "case does not exist".
        caseStore.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        return queryStore.findByCaseId(caseId);
    }
}

