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

import eu.openanalytics.rdepot.crane.security.OpenIdReAuthorizeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Properties;

@EnableWebMvc
@SpringBootApplication
public class CraneApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CraneApplication.class);
        app.setDefaultProperties(getDefaultProperties());
        app.run(args);
    }

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository, OAuth2AuthorizedClientRepository clientRepository) {
        return new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, clientRepository);
    }

    @Bean
    public OpenIdReAuthorizeFilter openIdReAuthorizeFilter() {
        return new OpenIdReAuthorizeFilter();
    }

    @Bean
    public OrderedRequestContextFilter orderedRequestContextFilter() {
        return new OrderedRequestContextFilter();
    }

    private static Properties getDefaultProperties() {
        Properties properties = new Properties();

        // Health configuration
        // ====================

        properties.put("management.server.port", "9090");

        // disable ldap health endpoint
        properties.put("management.health.ldap.enabled", false);
        // disable default redis health endpoint since it's managed by redisSession
        properties.put("management.health.redis.enabled", false);
        // enable Kubernetes probes
        properties.put("management.endpoint.health.probes.enabled", true);

        // use in-memory session storage by default. Can be overwritten in application.yml
        properties.put("spring.session.store-type", "none");

        // ====================

        return properties;
    }

}
