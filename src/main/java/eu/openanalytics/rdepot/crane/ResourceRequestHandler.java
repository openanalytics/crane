/**
 * Crane
 *
 * Copyright (C) 2021-2022 Open Analytics
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
package eu.openanalytics.rdepot.crane;

import eu.openanalytics.rdepot.crane.model.CacheRule;
import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.http.CacheControl;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ResourceRequestHandler implements HttpRequestHandler {

    private final Repository repository;
    private final ResourceHttpRequestHandler resourceHttpRequestHandler;
    private final Map<AntPathRequestMatcher, String> cacheRules;
    private final RedirectIfDirectoryHandler redirectIfDirectoryHandler;

    public ResourceRequestHandler(Repository repository, ResourceHttpRequestHandler resourceHttpRequestHandler, RedirectIfDirectoryHandler redirectIfDirectoryHandler) {
        this.repository = repository;
        this.resourceHttpRequestHandler = resourceHttpRequestHandler;
        this.redirectIfDirectoryHandler = redirectIfDirectoryHandler;
        this.cacheRules = new HashMap<>();

        if (repository.getCache() != null) {
            for (CacheRule cache : repository.getCache()) {
                cacheRules.put(
                    new AntPathRequestMatcher(cache.getPattern()),
                    CacheControl.maxAge(cache.getMaxAge()).getHeaderValue()
                );
            }
        }
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getServletPath().endsWith("/")) {
            // TODO allow to generate index file
            request.getRequestDispatcher(request.getServletPath() + repository.getIndexFileName()).forward(request, response);
            return;
        }
        if (redirectIfDirectoryHandler.handleRequest(request, response)) {
            return;
        }
        addCachingHeaders(request, response);
        resourceHttpRequestHandler.handleRequest(request, response);
    }

    private void addCachingHeaders(HttpServletRequest request, HttpServletResponse response) {
        for (Map.Entry<AntPathRequestMatcher, String> cacheRule : cacheRules.entrySet()) {
            if (cacheRule.getKey().matches(request)) {
                response.setHeader("Cache-Control", cacheRule.getValue());
                break;
            }
        }
    }

}
