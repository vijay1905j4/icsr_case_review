package com.theragenx.icsr.service;

import com.theragenx.icsr.model.CreateQueryRequest;
import com.theragenx.icsr.model.Query;
import com.theragenx.icsr.store.CaseStore;
import com.theragenx.icsr.store.QueryStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query service: creates and retrieves reviewer queries.
 * Validates that the target caseId exists before persisting.
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
     * Creates a new query, assigning a UUID and timestamp.
     *
     * @throws com.theragenx.icsr.exception.CaseNotFoundException if caseId does not exist
     */
    public Query createQuery(CreateQueryRequest request) {
        // TODO: verify caseStore.findById(request.caseId()).isPresent() else throw 404
        //       build Query with UUID.randomUUID(), Instant.now()
        //       queryStore.save(query); return query
        throw new UnsupportedOperationException("TODO: createQuery");
    }

    /**
     * Returns all queries for the given case, in creation order.
     *
     * @throws com.theragenx.icsr.exception.CaseNotFoundException if caseId does not exist
     */
    public List<Query> listQueries(String caseId) {
        // TODO: verify case exists, then return queryStore.findByCaseId(caseId)
        throw new UnsupportedOperationException("TODO: listQueries");
    }
}
