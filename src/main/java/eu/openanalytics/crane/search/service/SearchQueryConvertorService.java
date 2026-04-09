/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.crane.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.SearchRequest;
import eu.openanalytics.crane.search.model.dto.SearchQueryDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnBean(Client.class)
public class SearchQueryConvertorService {

    public SearchRequest convert(SearchQueryDto searchQuery) {
        SearchRequest.SearchRequestBuilder searchRequestBuilder = SearchRequest.builder();
        if (searchQuery.q() != null) {
            searchRequestBuilder.q(searchQuery.q());
        }
        if (searchQuery.attributesToHighlight() != null) {
            searchRequestBuilder.attributesToHighlight(toArray(searchQuery.attributesToHighlight()));
        }
        if (searchQuery.facets() != null) {
            searchRequestBuilder.facets(toArray(searchQuery.facets()));
        }
        if (searchQuery.filter() != null) {
            searchRequestBuilder.filterArray(toNestedArray(searchQuery.filter()));
        }
        searchRequestBuilder.limit(searchQuery.limit());
        searchRequestBuilder.offset(searchQuery.offset());
        if (searchQuery.highlightPostTag() != null) {
            searchRequestBuilder.highlightPostTag(searchQuery.highlightPostTag());
        }
        if (searchQuery.highlightPreTag() != null) {
            searchRequestBuilder.highlightPreTag(searchQuery.highlightPreTag());
        }
        return searchRequestBuilder.build();
    }

    private String[][] toNestedArray(List<List<String>> list) {
        return list.stream()
            .map(inner -> inner.toArray(new String[0]))
            .toArray(String[][]::new);
    }

    private String[] toArray(List<String> list) {
        return list.toArray(new String[0]);
    }

}
