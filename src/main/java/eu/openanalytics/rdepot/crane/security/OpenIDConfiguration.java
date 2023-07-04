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
package eu.openanalytics.rdepot.crane.security;

import org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration
public class OpenIDConfiguration {

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository, OAuth2AuthorizedClientRepository clientRepository) {
        return new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, clientRepository);
    }

    @Bean
    public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(Environment environment, ClientRegistrationRepository clientRegistrationRepository, RedisTemplate<String, OAuth2AuthorizedClient> oAuth2AuthorizedClientRedisTemplate) {
        if (environment.getProperty("spring.session.store-type", "none").equals("redis")) {
            return new RedisOAuth2AuthorizedClientService(oAuth2AuthorizedClientRedisTemplate);
        }
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public RedisTemplate<String, OAuth2AuthorizedClient> oAuth2AuthorizedClientRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OAuth2AuthorizedClient> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public OpenIdReAuthorizeFilter openIdReAuthorizeFilter(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager, OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        return new OpenIdReAuthorizeFilter(oAuth2AuthorizedClientManager, oAuth2AuthorizedClientService);
    }

    @Bean
    public OrderedRequestContextFilter orderedRequestContextFilter() {
        return new OrderedRequestContextFilter();
    }

}
