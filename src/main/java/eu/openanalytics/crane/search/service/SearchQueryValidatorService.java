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
import eu.openanalytics.crane.search.exception.InvalidSearchQueryException;
import eu.openanalytics.crane.search.model.dto.SearchQueryDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnBean(Client.class)
public class SearchQueryValidatorService {

    public static final List<String> ALLOWED_FACETS = List.of(
        "metadata.humanReadableMediaType",
        "metadata.language",
        "metadata.year",
        "metadata.month",
        "metadata.day" ,
        "metadata.authors",
        "metadata.hide" // TODO
    );

    public SearchQueryDto validate(SearchQueryDto searchQuery) {
        if (searchQuery.facets() != null) {
            validateFacets(searchQuery.facets());
        }
        if (searchQuery.filter() != null) {
            validateFilters(searchQuery.filter());
        }
        if (searchQuery.attributesToHighlight() != null) {
            validateAttributesToHighlight(searchQuery.attributesToHighlight());
        }
        if (searchQuery.highlightPreTag() != null && !searchQuery.highlightPreTag().equals("__ais-highlight__")) {
            throw new InvalidSearchQueryException("Invalid highlightPreTag");
        }
        if (searchQuery.highlightPostTag() != null && !searchQuery.highlightPostTag().equals("__/ais-highlight__")) {
            throw new InvalidSearchQueryException("Invalid highlightPostTag");
        }
        if (searchQuery.limit() != null && (searchQuery.limit() < 0 || searchQuery.limit() > 100)) {
            throw new InvalidSearchQueryException("Invalid limit ");
        }
        if (searchQuery.offset() != null && searchQuery.offset() < 0) {
            throw new InvalidSearchQueryException("Invalid offset");
        }
        if (searchQuery.distinct() != null && !searchQuery.distinct().equals("webUri")) {
            throw new InvalidSearchQueryException("Invalid distinct");
        }
        return searchQuery;
    }

    private void validateFacets(List<String> facets) {
        for (String facet : facets) {
            if (!ALLOWED_FACETS.contains(facet)) {
                throw new InvalidSearchQueryException(String.format("Invalid facet '%s'", facet));
            }
        }
    }

    private void validateFilters(List<List<String>> filters) {
        for (List<String> filter : filters) {
            for (String field : filter) {
                if (field.contains("repositoryName")) {
                    throw new InvalidSearchQueryException("Invalid filter");
                }
                if (field.startsWith("webUri STARTS WITH \"") || field.startsWith("webUri = \"")) {
                    // allowed
                    continue;
                }
                validateFilter(field);
            }
        }
    }

    private void validateFilter(String filter) {
        for (String filterOperator : List.of("!=", "=")) {
            // TODO value may contain filterOperation
            if (StringUtils.countMatches(filter, filterOperator) == 1) {
                String[] split = filter.split(filterOperator);
                if (!split[0].startsWith("\"") || !split[0].endsWith("\"")) {
                    throw new InvalidSearchQueryException("Invalid filter");
                }
                String fieldName = split[0].substring(1, split[0].length() - 1);
                if (!ALLOWED_FACETS.contains(fieldName)) {
                    throw new InvalidSearchQueryException("Invalid filter");
                }
                return;
            }
        }
        throw new InvalidSearchQueryException("Invalid filter");
    }

    private void validateAttributesToHighlight(List<String> attributesToHighlight) {
        if (!attributesToHighlight.equals(List.of("*"))) {
            throw new InvalidSearchQueryException("Invalid attributesToHighlight");
        }
    }

}
