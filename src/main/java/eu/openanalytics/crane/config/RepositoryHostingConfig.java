/**
 * Crane
 *
 * Copyright (C) 2021-2024 Open Analytics
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
package eu.openanalytics.crane.config;

import eu.openanalytics.crane.model.config.Repository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class RepositoryHostingConfig {

    private final CraneConfig config;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public RepositoryHostingConfig(CraneConfig config) {
        this.config = config;
    }

    @Bean
    public SimpleUrlHandlerMapping handler() {
        Map<String, HttpRequestHandler> urlMap = new LinkedHashMap<>();

        for (Repository repository : config.getRepositories()) {
            urlMap.put(String.format("/%s/**", repository.getName()), (request, response) -> {
                request.getRequestDispatcher("/__file" + urlPathHelper.getPathWithinApplication(request)).forward(request, response);
            });
        }

        return new SimpleUrlHandlerMapping(urlMap);
    }

}
