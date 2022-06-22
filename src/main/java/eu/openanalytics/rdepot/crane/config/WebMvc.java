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
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebMvc implements WebMvcConfigurer {

    private final CraneConfig config;

    public WebMvc(CraneConfig config) {
        this.config = config;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (Repository repository : config.getRepositories()) {
            registry
                .addResourceHandler(String.format("/%s/**", repository.getName()))
                .addResourceLocations(String.format("file://%s/%s/", config.getStorageLocation(), repository.getName()));
        }
    }

}
