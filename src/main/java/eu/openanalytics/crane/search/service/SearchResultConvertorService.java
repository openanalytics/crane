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
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.Searchable;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.search.exception.InternalSearchException;
import eu.openanalytics.crane.search.model.dto.SearchHit;
import eu.openanalytics.crane.search.model.dto.SearchResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBean(Client.class)
public class SearchResultConvertorService {

    private final SearchConfig searchConfig;

    public SearchResultConvertorService(SearchConfig searchConfig) {
        this.searchConfig = searchConfig;
    }

    @SuppressWarnings("unchecked")
    public SearchResultDto convert(Searchable result) {
        if (!(result instanceof SearchResult searchResult)) {
            throw new InternalSearchException("MeiliSearch result is not of type SearchResult");
        }
        SearchResultDto.SearchResultDtoBuilder builder = SearchResultDto.builder();

        if (!(searchResult.getFacetDistribution() instanceof Map)) {
            throw new InternalSearchException("No FacetDistribution in search result");
        }
        builder.facetDistribution(convertFacetDistribution((Map<String, Map<String, Object>>) searchResult.getFacetDistribution()));
        builder.hits(convertHits(searchResult.getHits()));

        builder.indexUid(searchConfig.getIndexUid());
        builder.query(searchResult.getQuery());
        builder.processingTimeMs(searchResult.getProcessingTimeMs());
        builder.limit(searchResult.getLimit());
        builder.offset(searchResult.getOffset());
        builder.estimatedTotalHits(searchResult.getEstimatedTotalHits());

        return builder.build();
    }

    private List<SearchHit> convertHits(ArrayList<HashMap<String, Object>> hits) {
        List<SearchHit> result = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            result.add(convertHit(hit));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private SearchHit convertHit(Map<String, Object> hit) {
        SearchHit.SearchHitBuilder builder = SearchHit.builder();
        builder.id(getRequiredKeyAsString("id", hit));
        builder.webUri(getRequiredKeyAsString("webUri", hit));
        builder.repositoryName(getRequiredKeyAsString("repositoryName", hit));
        builder.chunk(getRequiredKeyAsString("chunk", hit));
        builder.chunkType(getRequiredKeyAsString("chunkType", hit));
        Map<String, Object> formatted = getOptionalKey("_formatted", hit, Map.class);
        if (formatted != null) {
            builder._formatted(convertHit(formatted));
        }
        builder.extractedMetadata(getRequiredKey("extractedMetadata", hit, Map.class));
        return builder.build();
    }

    private Map<String, Map<String, Object>> convertFacetDistribution(Map<String, Map<String, Object>> facetDistribution) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : facetDistribution.entrySet()) {
            if (SearchQueryValidatorService.ALLOWED_FACETS.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String getRequiredKeyAsString(String key, Map<String, Object> hit) {
        if (!hit.containsKey(key)) {
            throw new InternalSearchException(String.format("Missing key %s from hit", key));
        }
        Object value = hit.get(key);
        if (!(value instanceof String str)) {
            throw new InternalSearchException(String.format("Key %s from hit is not a string", key));
        }
        return str;
    }

    private <T> T getRequiredKey(String key, Map<String, Object> hit, Class<T> clazz) {
        if (!hit.containsKey(key)) {
            throw new InternalSearchException(String.format("Missing key %s from hit", key));
        }
        if (!clazz.isInstance(hit.get(key))) {
            throw new InternalSearchException(String.format("Key %s from hit isn't the expected type", key));
        }
        return clazz.cast(hit.get(key));
    }

    private <T> T getOptionalKey(String key, Map<String, Object> hit, Class<T> clazz) {
        if (!hit.containsKey(key)) {
            return null;
        }
        if (!clazz.isInstance(hit.get(key))) {
            throw new InternalSearchException(String.format("Key %s from hit isn't the expected type", key));
        }
        return clazz.cast(hit.get(key));
    }

}
