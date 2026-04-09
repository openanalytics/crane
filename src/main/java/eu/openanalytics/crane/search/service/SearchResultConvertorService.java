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
import eu.openanalytics.crane.search.model.dto.SearchChunk;
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

    @SuppressWarnings("unchecked")
    public SearchResultDto convert(Searchable result) {
        if (!(result instanceof SearchResult searchResult)) {
            throw new IllegalStateException("TODO"); // todo proper exception
        }
        SearchResultDto.SearchResultDtoBuilder builder = SearchResultDto.builder();

        if (!(searchResult.getFacetDistribution() instanceof Map)) {
            throw new IllegalStateException("TODO"); // todo proper exception
        }
        builder.facetDistribution(convertFacetDistribution((Map<String, Map<String, Object>>) searchResult.getFacetDistribution()));
        builder.hits(convertHits(searchResult.getHits()));

        builder.indexUid("files"); // TODO
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
        // TODO error + cleanup
        if (hit.containsKey("id")) {
            builder.id(hit.get("id").toString());
        }
        if (hit.containsKey("pathRelativeToRepository")) {
            builder.pathRelativeToRepository(hit.get("pathRelativeToRepository").toString());
        }
        if (hit.containsKey("repositoryName")) {
            builder.repositoryName(hit.get("repositoryName").toString());
        }
        if (hit.containsKey("chunks") && hit.get("chunks") instanceof List<?> chunks) {
            builder.chunks(convertChunks(chunks));
        }
        if (hit.containsKey("_formatted")) {
            if (hit.get("_formatted") instanceof Map<?, ?>) {
                builder._formatted(convertHit((Map<String, Object>) hit.get("_formatted")));
            }
        }
        if (hit.containsKey("extractedMetadata") && hit.get("extractedMetadata") instanceof Map<?,?> extractedMetadata) {
            // TODO filter
            builder.extractedMetadata((Map<String, String>) extractedMetadata);
        }

        return builder.build();
    }

    private List<SearchChunk> convertChunks(List<?> chunks) {
        List<SearchChunk> result = new ArrayList<>();
        for (Object chunk: chunks) {
            if (chunk instanceof Map<?, ?> map) {
                if (map.containsKey("type") && map.containsKey("text")) {
                    result.add(new SearchChunk(map.get("type").toString(), map.get("text").toString()));
                }
            }
        }
        return result;
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

}
