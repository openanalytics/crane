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
package eu.openanalytics.crane.search.controller;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import eu.openanalytics.crane.search.exception.InternalSearchException;
import eu.openanalytics.crane.search.exception.InvalidSearchQueryException;
import eu.openanalytics.crane.search.model.dto.SearchApiInfoDTo;
import eu.openanalytics.crane.search.model.dto.SearchQueriesDto;
import eu.openanalytics.crane.search.model.dto.SearchResultDto;
import eu.openanalytics.crane.search.service.SearchQueryConvertorService;
import eu.openanalytics.crane.search.service.SearchQueryValidatorService;
import eu.openanalytics.crane.search.service.SearchResultConvertorService;
import eu.openanalytics.crane.search.service.SearchResultFilterService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(Client.class)
public class SearchController {

    private final Index index;
    private final SearchQueryValidatorService searchQueryValidatorService;
    private final SearchQueryConvertorService searchQueryConvertorService;
    private final SearchResultConvertorService searchResultConvertorService;
    private final SearchResultFilterService searchResultFilterService;
    private final SearchConfig searchConfig;
    private final CraneConfig craneConfig;

    public SearchController(Client client,
                            SearchConfig searchConfig,
                            CraneConfig craneConfig,
                            SearchQueryValidatorService searchQueryValidatorService,
                            SearchQueryConvertorService searchQueryConvertorService,
                            SearchResultConvertorService searchResultConvertorService,
                            SearchResultFilterService searchResultFilterService) {
        this.craneConfig = craneConfig;
        this.searchQueryValidatorService = searchQueryValidatorService;
        this.searchQueryConvertorService = searchQueryConvertorService;
        this.searchResultConvertorService = searchResultConvertorService;
        this.searchResultFilterService = searchResultFilterService;
        this.searchConfig = searchConfig;
        index = client.index(searchConfig.getIndexUid());
    }

    @ResponseBody
    @PreAuthorize("@readAccessControlService.canAccess(#r, '')")
    @PostMapping(value = "/__search/{repository}/multi-search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
        @RequestBody SearchQueriesDto queries,
        @P("r") @PathVariable(name = "repository") String stringRepository) {

        try {
            Repository repository = craneConfig.getRepository(stringRepository);
            if (repository == null) {
                throw new InternalSearchException("Invalid repository");
            }

            List<SearchResultDto> results = queries.queries()
                .stream()
                .map(searchQueryValidatorService::validate)
                .map(searchQuery -> searchQueryConvertorService.convert(repository, searchQuery))
                .map(index::search)
                .map(searchResultConvertorService::convert)
                .map(searchResult -> searchResultFilterService.filter(repository, searchResult))
                .toList();

            return ResponseEntity.ok(Map.of("results", results));
        } catch (InvalidSearchQueryException e) {
            return ApiResponse.fail("Invalid search query: " + e.getMessage());
        } catch (InternalSearchException e) {
            return ApiResponse.fail("Internal search error: " + e.getMessage());
        }
    }

    @GetMapping(value = "/__search/api-info")
    public ResponseEntity<ApiResponse<SearchApiInfoDTo>> searchApiInfo() {
        return ApiResponse.success(new SearchApiInfoDTo(searchConfig.getIndexUid()));
    }

}
