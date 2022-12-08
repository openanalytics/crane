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
package eu.openanalytics.rdepot.crane.config;

import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.ServletContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ResourceServerConfig {

    private final CraneConfig config;

    private final ApplicationContext applicationContext;

    private final ServletContext servletContext;

    public ResourceServerConfig(CraneConfig config, ApplicationContext applicationContext, ServletContext servletContext) {
        this.config = config;
        this.applicationContext = applicationContext;
        this.servletContext = servletContext;
    }

    @Bean
    public SimpleUrlHandlerMapping handler() throws Exception {
        Map<String, HttpRequestHandler> urlMap = new LinkedHashMap<>();

        for (Repository repository : config.getRepositories()) {
            ResourceHttpRequestHandler resourceHttpRequestHandler = new ResourceHttpRequestHandler();
            resourceHttpRequestHandler.setApplicationContext(applicationContext);
            resourceHttpRequestHandler.setServletContext(servletContext);
            if (config.getStorageLocation().startsWith("s3://")) {
                resourceHttpRequestHandler.setLocationValues(List.of(String.format("%s/%s/", config.getStorageLocation(), repository.getName())));
            } else {
                resourceHttpRequestHandler.setLocationValues(List.of(String.format("file://%s/%s/", config.getStorageLocation(), repository.getName())));
            }
            resourceHttpRequestHandler.setResourceResolvers(List.of(new PathResourceResolver()));
            resourceHttpRequestHandler.afterPropertiesSet();

            urlMap.put(String.format("/%s/**", repository.getName()), (request, response) -> {
                if (request.getServletPath().endsWith("/")) {
                    // TODO allow generate to index file
                    request.getRequestDispatcher(request.getServletPath() + repository.getIndexFileName()).forward(request, response);
                } else {
                    resourceHttpRequestHandler.handleRequest(request, response);
                }
            });
        }

        return new SimpleUrlHandlerMapping(urlMap);
    }

}
