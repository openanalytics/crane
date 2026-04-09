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
import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.search.model.dto.SearchHit;
import eu.openanalytics.crane.search.model.dto.SearchResultDto;
import eu.openanalytics.crane.service.PathReadAccessControlService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBean(Client.class)
public class SearchResultFilterService {

    private final PathReadAccessControlService pathReadAccessControlService;
    private final CraneConfig craneConfig;

    public SearchResultFilterService(PathReadAccessControlService pathReadAccessControlService, CraneConfig craneConfig) {
        this.pathReadAccessControlService = pathReadAccessControlService;
        this.craneConfig = craneConfig;
    }

    public SearchResultDto filter(SearchResultDto searchResult) {
        return searchResult.toBuilder()
            .hits(searchResult.hits().stream().map(this::filterHit).toList())
            .build();
    }

    private SearchHit filterHit(SearchHit hit) {
        Repository repository = craneConfig.getRepository(hit.repositoryName());
        if (repository == null) {
            // todo log warning
            return  new SearchHit("dummy", "", List.of(), "", Map.of(), null);
        }
        CranePath cranePath = new CranePath(repository, hit.pathRelativeToRepository());
        if (pathReadAccessControlService.canAccess(cranePath)) {
            return hit;
        }
        return  new SearchHit("dummy", "", List.of(), "", Map.of(), null);
    }

}
