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
package eu.openanalytics.crane.security;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.util.UrlPathHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurity {

    private final CraneConfig config;

    private final OpenIdReAuthorizeFilter openIdReAuthorizeFilter;

    private final SpecExpressionResolver specExpressionResolver;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AuditingService auditingService;
    private final TokenParser tokenParser;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public WebSecurity(CraneConfig config, OpenIdReAuthorizeFilter openIdReAuthorizeFilter, SpecExpressionResolver specExpressionResolver, AuditingService auditingService) {
        this.config = config;
        this.openIdReAuthorizeFilter = openIdReAuthorizeFilter;
        this.specExpressionResolver = specExpressionResolver;
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
                                "/__file/**",
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
                        .access(this::check)
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

    private AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        String path = urlPathHelper.getRequestUri(context.getRequest());
        if (path.contains("%")) {
            // don't support encoded paths
            return new AuthorizationDecision(false);
        }

        File absolutePath = new File(path);

        try {
            String canonicalPath = absolutePath.getCanonicalPath();
            if (!new File(canonicalPath).isAbsolute()) {
                return new AuthorizationDecision(false);
            }
            if (!absolutePath.getAbsolutePath().equals(canonicalPath)) {
                return new AuthorizationDecision(false);
            }
        } catch (IOException e) {
            return new AuthorizationDecision(false);
        }
        String repository = Path.of(path).iterator().next().toString();
        return new AuthorizationDecision(config.getRepository(repository) != null);
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
