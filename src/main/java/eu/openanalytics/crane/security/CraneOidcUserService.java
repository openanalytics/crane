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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class CraneOidcUserService extends OidcUserService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TokenParser tokenParser;
    private final CraneConfig config;

    public CraneOidcUserService(TokenParser tokenParser, CraneConfig config) {
        this.tokenParser = tokenParser;
        this.config = config;
    }

    @Override
    public OidcCraneUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
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

        return new OidcCraneUser(
            tokenParser.parseAuthorities(user.getClaims()),
            user.getIdToken(),
            user.getUserInfo(),
            tokenParser.parseUID(user.getClaims()),
            tokenParser.parseGIDS(user.getClaims()),
            config.getOpenidUsernameClaim()
        );
    }
}
