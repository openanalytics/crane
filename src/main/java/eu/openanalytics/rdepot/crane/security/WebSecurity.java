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

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.security.auditing.LogoutHandlerAuditEvent;
import eu.openanalytics.rdepot.crane.service.PathAccessControlService;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionResolver;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurity {

    private final CraneConfig config;

    private final OpenIdReAuthorizeFilter openIdReAuthorizeFilter;

    private final SpecExpressionResolver specExpressionResolver;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PathAccessControlService pathAccessControlService;

    private final ApplicationEventPublisher publisher;

    public WebSecurity(CraneConfig config, OpenIdReAuthorizeFilter openIdReAuthorizeFilter, SpecExpressionResolver specExpressionResolver, PathAccessControlService pathAccessControlService, ApplicationEventPublisher publisher) {
        this.config = config;
        this.openIdReAuthorizeFilter = openIdReAuthorizeFilter;
        this.specExpressionResolver = specExpressionResolver;
        this.pathAccessControlService = pathAccessControlService;
        this.publisher = publisher;
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/",
                    "/.well-known/configured-openid-configuration",
                    "/__index",
                    "/__index/webjars/**",
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/auditevents",
                    "/error",
                    "/logout-success"
                ).permitAll()
                .requestMatchers("/{repoName}/**")
                .access((authentication, context) -> new AuthorizationDecision(pathAccessControlService.canAccess(authentication.get(), context.getRequest())))
                .anyRequest().authenticated())
            .exceptionHandling(exception -> exception.accessDeniedPage("/error"))
            .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwkSetUri(config.getJwksUri()).jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .oauth2Login(login -> login.userInfoEndpoint(endpoint -> endpoint.userAuthoritiesMapper(new NullAuthoritiesMapper()).oidcUserService(oidcUserService())))
            .oauth2Client(withDefaults())
            .logout(logout -> logout.logoutSuccessHandler(getLogoutSuccessHandler()))
            .addFilterAfter(openIdReAuthorizeFilter, UsernamePasswordAuthenticationFilter.class);
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
            publisher.publishEvent(new LogoutHandlerAuditEvent(httpServletRequest, httpServletResponse, authentication));
            SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();
            delegate.setDefaultTargetUrl(resolvedLogoutUrl);
            delegate.onLogoutSuccess(httpServletRequest, httpServletResponse, authentication);
        };
    }

    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        // Use a custom UserService that respects our username attribute config and extract the authorities from the ID token
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                Object claimValue = userRequest.getIdToken().getClaims().get(config.getOpenidGroupsClaim());
                Set<GrantedAuthority> mappedAuthorities = mapAuthorities(claimValue);

                return new DefaultOidcUser(mappedAuthorities, userRequest.getIdToken(), config.getOpenidUsernameClaim());
            }
        };
    }

    /**
     * Authorities mapper when an Oauth2 JWT is used.
     * I.e. when the user is authenticated by passing an OAuth2 Access token as Bearer token in the Authorization header.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (!config.hasOpenidGroupsClaim()) {
                return new ArrayList<>();
            }
            Object claimValue = jwt.getClaims().get(config.getOpenidGroupsClaim());
            return mapAuthorities(claimValue);
        });
        converter.setPrincipalClaimName(config.getOpenidUsernameClaim());
        return converter;
    }

    /**
     * Maps the groups provided in the claimValue to {@link GrantedAuthority}.
     *
     * @return
     */
    private Set<GrantedAuthority> mapAuthorities(Object claimValue) {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
        for (String role : parseGroupsClaim(claimValue)) {
            String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
            mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
        }
        return mappedAuthorities;
    }

    /**
     * Parses the claim containing the groups to a List of Strings.
     */
    private List<String> parseGroupsClaim(Object claimValue) {
        String groupsClaimName = config.getOpenidGroupsClaim();
        if (claimValue == null) {
            logger.debug(String.format("No groups claim with name %s found", groupsClaimName));
            return new ArrayList<>();
        } else {
            logger.debug(String.format("Matching claim found: %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
        }

        if (claimValue instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object object : ((Collection<?>) claimValue)) {
                if (object != null) {
                    result.add(object.toString());
                }
            }
            logger.debug(String.format("Parsed groups claim as Java Collection: %s -> %s (%s)", groupsClaimName, result, result.getClass()));
            return result;
        }

        if (claimValue instanceof String) {
            List<String> result = new ArrayList<>();
            try {
                Object value = new JSONParser(JSONParser.MODE_PERMISSIVE).parse((String) claimValue);
                if (value instanceof List valueList) {
                    valueList.forEach(o -> result.add(o.toString()));
                }
            } catch (ParseException e) {
                // Unable to parse JSON
                logger.debug(String.format("Unable to parse claim as JSON: %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
            }
            logger.debug(String.format("Parsed groups claim as JSON: %s -> %s (%s)", groupsClaimName, result, result.getClass()));
            return result;
        }

        logger.debug(String.format("No parser found for groups claim (unsupported type): %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
        return new ArrayList<>();
    }

}
