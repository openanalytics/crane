/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurity {

    private final CraneConfig config;
    private final OpenIdReAuthorizeFilter openIdReAuthorizeFilter;
    private final SpecExpressionResolver specExpressionResolver;
    private final AuditingService auditingService;
    private final TokenParser tokenParser;

    public WebSecurity(CraneConfig config, OpenIdReAuthorizeFilter openIdReAuthorizeFilter, SpecExpressionResolver specExpressionResolver, AuditingService auditingService) {
        this.config = config;
        this.openIdReAuthorizeFilter = openIdReAuthorizeFilter;
        this.specExpressionResolver = specExpressionResolver;
        this.auditingService = auditingService;
        this.tokenParser = new TokenParser(config);
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http, SavedRequestAwareAuthenticationSuccessHandler successHandler, CraneConfig craneConfig) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        List<String> repoMatchers = craneConfig.getRepositories().stream().map(r -> "/" + r.getName() + "/**").toList();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/",
                                "/.well-known/configured-openid-configuration",
                                "/favicon.ico",
                                "/__file/**",
                                "/__index",
                                "/__assets/css/**",
                                "/__assets/webjars/**",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/auditevents",
                                "/error",
                                "/logout-success"
                        ).permitAll()
                        .requestMatchers(repoMatchers.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.accessDeniedPage("/error"))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwkSetUri(config.getJwksUri()).jwtAuthenticationConverter(new CraneJwtAuthenticationConverter(tokenParser, config))))
                .oauth2Login(login -> login
                        .userInfoEndpoint(endpoint -> endpoint.userAuthoritiesMapper(new NullAuthoritiesMapper()).oidcUserService(new CraneOidcUserService(tokenParser, config)))
                        .successHandler(successHandler)
                )
                .oauth2Client(withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(getLogoutSuccessHandler()))
                .addFilterBefore(new PathTraversalFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BlockInternalUrlFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CustomExceptionTranslationFilter(), ExceptionTranslationFilter.class)
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

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler savedRequestAwareAuthenticationSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler savedRequestAwareAuthenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        savedRequestAwareAuthenticationSuccessHandler.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
                String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
                if (redirectUrl.contains("/__file/")) {
                    redirectUrl = redirectUrl.replace("/__file/", "/");
                }
                if (redirectUrl.contains("/__index/")) {
                    redirectUrl = redirectUrl.replace("/__index/", "/");
                }
                response.sendRedirect(redirectUrl);
            }
        });
        return savedRequestAwareAuthenticationSuccessHandler;
    }

}
