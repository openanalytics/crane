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
package eu.openanalytics.crane.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "app.search")
public class SearchConfig {

    @Getter
    @Setter
    private String meiliSearchUrl;

    @Getter
    @Setter
    private String meiliSearchToken;

    @Getter
    @Setter
    private String indexUid = "crane";

    @Bean
    @ConditionalOnProperty("app.search.meili-search-url")
    public Client client() {
        return new Client(new Config(
            meiliSearchUrl,
            meiliSearchToken
        ));
    }

}
