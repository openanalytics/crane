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
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.*;

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

    public WebSecurity(CraneConfig config, OpenIdReAuthorizeFilter openIdReAuthorizeFilter, SpecExpressionResolver specExpressionResolver, CraneAccessControlService craneAccessControlService, AuditingService auditingService) {
        this.config = config;
        this.openIdReAuthorizeFilter = openIdReAuthorizeFilter;
        this.specExpressionResolver = specExpressionResolver;
        this.craneAccessControlService = craneAccessControlService;
        this.auditingService = auditingService;
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
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwkSetUri(config.getJwksUri()).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .oauth2Login(login -> login.userInfoEndpoint(endpoint -> endpoint.userAuthoritiesMapper(new NullAuthoritiesMapper()).oidcUserService(createOidcUserService())))
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

    protected OidcUserService createOidcUserService() {
        // Use a custom UserService that supports the 'emails' array attribute.
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                OidcUser user;
                try {
                    user = super.loadUser(userRequest);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Error while loading user info: {}", ex.getMessage());
                    throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST), "Error while loading user info", ex);
                } catch (OAuth2AuthenticationException ex) {
                    logger.warn("Error while loading user info: {}", ex.getMessage());
                    throw ex;
                }

                String nameAttributeKey = config.getPosixUIDAttribute();

                Object claimValue = userRequest.getIdToken().getClaims().get(config.getOpenidGroupsClaim());
                Set<GrantedAuthority> mappedAuthorities = mapAuthorities(claimValue);
                return new CustomOidcUser(mappedAuthorities,
                        user.getIdToken(),
                        user.getUserInfo(),
                        nameAttributeKey
                );
            }
        };
    }

    public static class CustomOidcUser extends DefaultOidcUser {

        private static final long serialVersionUID = 7563253562760236634L;

        private final int posixUID;

        public CustomOidcUser(Set<GrantedAuthority> authorities, OidcIdToken idToken, OidcUserInfo userInfo, String uidAttributeKey) {
            super(authorities, idToken, userInfo, "preferred_username");
            this.posixUID = parseUID(userInfo, uidAttributeKey);
        }

        private int parseUID(OidcUserInfo userInfo, String uidAttributeKey) {
            Object uid = userInfo.getClaim(uidAttributeKey);
            if (uid instanceof String) {
                return Integer.parseInt((String) uid);
            } else if (uid instanceof Integer) {
                return (int) uid;
            }
            return -1;
        }

        public int getPosixUID() {
            return posixUID;
        }

        public static CustomOidcUser of(Authentication auth, String uidAttributeKey) {
            if (auth instanceof JwtAuthenticationToken) {
                return of((JwtAuthenticationToken) auth, uidAttributeKey);
            }
            if (auth instanceof OAuth2AuthenticationToken) {
                return of((OAuth2AuthenticationToken) auth, uidAttributeKey);
            }
            throw new RuntimeException(String.format("Not implemented Authentication type %s", auth.getClass().toString()));
        }

        public static CustomOidcUser of(JwtAuthenticationToken token, String uidAttributeKey) {
            Jwt jwt = token.getToken();
            return new CustomOidcUser(
                    new HashSet<>(token.getAuthorities()),
                    new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims()),
                    new OidcUserInfo(jwt.getClaims()), uidAttributeKey
            );
        }

        public static CustomOidcUser of(OAuth2AuthenticationToken token, String uidAttributeKey) {
            return (CustomOidcUser) token.getPrincipal();
        }
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
