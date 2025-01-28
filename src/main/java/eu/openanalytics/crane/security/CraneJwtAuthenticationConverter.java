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
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Collection;
import java.util.Map;

public class CraneJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TokenParser tokenParser;
    private final CraneConfig config;

    public CraneJwtAuthenticationConverter(TokenParser tokenParser, CraneConfig config) {
        this.tokenParser = tokenParser;
        this.config = config;
    }

    public final CraneOAuth2Token convert(@NotNull Jwt jwt) {
        return new CraneOAuth2Token(
            jwt,
            new JwtCraneUser(
                jwt.getClaim(config.getOpenidUsernameClaim()),
                tokenParser.parseUID(jwt.getClaims()),
                tokenParser.parseGIDS(jwt.getClaims())
            ),
            jwt,
            tokenParser.parseAuthorities(jwt.getClaims())) {
        };
    }


    public static class CraneOAuth2Token extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

        protected CraneOAuth2Token(Jwt token, Object principal, Object credentials, Collection<? extends GrantedAuthority> grantedAuthorities) {
            super(token, principal, credentials, grantedAuthorities);
        }

        @Override
        public Map<String, Object> getTokenAttributes() {
            return getToken().getClaims();
        }

    }

}
