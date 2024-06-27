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
package eu.openanalytics.rdepot.crane.config;

import eu.openanalytics.rdepot.crane.RepositoryHostingHandler;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.security.auditing.AuditingService;
import eu.openanalytics.rdepot.crane.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class RepositoryHostingConfig {

    private final CraneConfig config;
    private final UserService userService;

    private final AuditingService auditingService;

    public RepositoryHostingConfig(CraneConfig config, AuditingService auditingService, UserService userService) {
        this.config = config;
        this.auditingService = auditingService;
        this.userService = userService;
    }

    @Bean
    public SimpleUrlHandlerMapping handler() {
        Map<String, HttpRequestHandler> urlMap = new LinkedHashMap<>();

        for (Repository repository : config.getRepositories()) {
            Path repositoryRoot = repository.getStoragePath().resolve(repository.getName());
            RepositoryHostingHandler resourceHttpRequestHandler = new RepositoryHostingHandler(repository, repositoryRoot, auditingService, userService);
            urlMap.put(String.format("/%s/**", repository.getName()), resourceHttpRequestHandler);
        }

        return new SimpleUrlHandlerMapping(urlMap);
    }

}
