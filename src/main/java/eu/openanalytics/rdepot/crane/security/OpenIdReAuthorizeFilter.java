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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;


public class OpenIdReAuthorizeFilter extends OncePerRequestFilter {

    private final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    private final Clock clock = Clock.systemUTC();

    // use clock skew of 40 seconds instead of 60 seconds. Otherwise, if the access token is valid for 1 minute, it would get refreshed at each request.
    private final Duration clockSkew = Duration.ofSeconds(40);

    public OpenIdReAuthorizeFilter(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager, OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.oAuth2AuthorizedClientManager = oAuth2AuthorizedClientManager;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken)) {
            logger.debug(String.format("No session auth: %s", request.getServletPath()));
            chain.doFilter(request, response);
            return;
        }

        String secFetchMode = request.getHeader("Sec-Fetch-Mode");
        if (secFetchMode != null && !secFetchMode.equals("navigate")) {
            logger.debug(String.format("Not trying re-authorization: %s", request.getServletPath()));
            chain.doFilter(request, response);
            return;
        }
        logger.debug(String.format("Trying re-authorization: %s", request.getServletPath()));
        String clientId = ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId();
        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(clientId, auth.getName());

        if (authorizedClient == null) {
            invalidateSession(request, response, auth);
        } else {
            if (accessTokenExpired(authorizedClient)) {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withAuthorizedClient(authorizedClient)
                    .principal(auth)
                    .build();

                try {
                    oAuth2AuthorizedClientManager.authorize(authorizeRequest);
                    logger.info(String.format("OpenID access token refreshed [user: %s, request: %s]", auth.getName(), request.getServletPath()));
                } catch (ClientAuthorizationException ex) {
                    invalidateSession(request, response, auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * See {@link RefreshTokenOAuth2AuthorizedClientProvider}
     */
    private boolean accessTokenExpired(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null || authorizedClient.getAccessToken() == null || authorizedClient.getAccessToken().getExpiresAt() == null) {
            return true;
        }
        return clock.instant().isAfter(authorizedClient.getAccessToken().getExpiresAt().minus(this.clockSkew));
    }

    private void invalidateSession(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, Authentication auth) throws IOException {
        logger.info(String.format("OpenID access token expired, invalidating internal session [user: %s, request: %s]", auth.getName(), request.getServletPath()));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

}
