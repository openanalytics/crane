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
package eu.openanalytics.rdepot.crane.security;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.security.auditing.AuditingService;
import eu.openanalytics.rdepot.crane.service.CraneAccessControlService;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurity {

    private final CraneConfig config;

    private final OpenIdReAuthorizeFilter openIdReAuthorizeFilter;

    private final SpecExpressionResolver specExpressionResolver;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CraneAccessControlService craneAccessControlService;
    private final AuditingService auditingService;
    private final TokenParser tokenParser;

    public WebSecurity(CraneConfig config, OpenIdReAuthorizeFilter openIdReAuthorizeFilter, SpecExpressionResolver specExpressionResolver, CraneAccessControlService craneAccessControlService, AuditingService auditingService) {
        this.config = config;
        this.openIdReAuthorizeFilter = openIdReAuthorizeFilter;
        this.specExpressionResolver = specExpressionResolver;
        this.craneAccessControlService = craneAccessControlService;
        this.auditingService = auditingService;
        this.tokenParser = new TokenParser(config);
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/",
                                "/.well-known/configured-openid-configuration",
                                "/favicon.ico",
                                "/__index",
                                "/__index/css/**",
                                "/__index/webjars/**",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/auditevents",
                                "/error",
                                "/logout-success"
                        ).permitAll()
                        .requestMatchers("/{repoName}/**")
                        .access((authentication, context) -> new AuthorizationDecision(craneAccessControlService.canAccess(authentication.get(), context.getRequest())))
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.accessDeniedPage("/error"))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwkSetUri(config.getJwksUri()).jwtAuthenticationConverter(new CraneJwtAuthenticationConverter(tokenParser, config))))
                .oauth2Login(login -> login.userInfoEndpoint(endpoint -> endpoint.userAuthoritiesMapper(new NullAuthoritiesMapper()).oidcUserService(new CraneOidcUserService(tokenParser, config))))
                .oauth2Client(withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(getLogoutSuccessHandler()))
                .addFilterAfter(openIdReAuthorizeFilter, UsernamePasswordAuthenticationFilter.class)
                .requestCache((cache) -> cache.requestCache(requestCache));
        return http.build();
    }

    public LogoutSuccessHandler getLogoutSuccessHandler() {
        return (httpServletRequest, httpServletResponse, authentication) -> {
            String resolvedLogoutUrl = "/logout-success";
            if (config.getOpenidLogoutUrl() != null) {
                if (authentication != null) {
                    SpecExpressionContext context = SpecExpressionContext.create(authentication.getPrincipal(), authentication.getCredentials());
                    resolvedLogoutUrl = specExpressionResolver.evaluateToString(config.getOpenidLogoutUrl(), context);
                } else {
                    resolvedLogoutUrl = config.getOpenidLogoutUrl();
                }
            }
            auditingService.createLogoutHandlerAuditEvent(httpServletRequest);
            SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();
            delegate.setDefaultTargetUrl(resolvedLogoutUrl);
            delegate.onLogoutSuccess(httpServletRequest, httpServletResponse, authentication);
        };
    }

}
