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
package eu.openanalytics.rdepot.crane.service.spel;


import eu.openanalytics.rdepot.crane.model.Repository;
import eu.openanalytics.rdepot.crane.service.AccessControlService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpecExpressionContext {

    private Repository repository;

    private List<String> groups;

    private Map<String, Object> claims;

    public List<String> getGroups() {
        return groups;
    }

    public Repository getRepository() {
        return repository;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting according to the provided regex and trimming each result
     */
    public List<String> toList(String attribute, String regex) {
        if (attribute == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(attribute.split(regex)).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on `,` and trimming each result
     */
    public List<String> toList(String attribute) {
        return toList(attribute, ",");
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on according to the provided regex.
     * Each result is trimmed and converted to lowercase.
     */
    public List<String> toLowerCaseList(String attribute, String regex) {
        if (attribute == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(attribute.split(regex)).map(it -> it.trim().toLowerCase()).collect(Collectors.toList());
    }

    /**
     * Convert a {@see String} to a list of strings, by splitting on `,`. Each result is trimmed and converted to lowercase.
     */
    public List<String> toLowerCaseList(String attribute) {
        return toLowerCaseList(attribute, ",");
    }

    /**
     *  Returns true when the provided value is in the list of allowed values.
     *  Both the attribute and allowed values are trimmed.
     */
    public boolean isOneOf(String attribute, String... allowedValues) {
        if (attribute == null) {
            return false;
        }
        return Arrays.stream(allowedValues).anyMatch(it -> it.trim().equals(attribute.trim()));
    }

    /**
     *  Returns true when the provided value is in the list of allowed values.
     *  Both the attribute and allowed values are trimmed and the comparison ignores casing of the values.
     */
    public boolean isOneOfIgnoreCase(String attribute, String... allowedValues) {
        if (attribute == null) {
            return false;
        }
        return Arrays.stream(allowedValues).anyMatch(it -> it.trim().equalsIgnoreCase(attribute.trim()));
    }

    public static SpecExpressionContext create(Object... objects) {
        SpecExpressionContext ctx = new SpecExpressionContext();
        for (Object o : objects) {
            if (o instanceof Repository) {
                ctx.repository = (Repository) o;
            } else if (o instanceof DefaultOidcUser) {
                ctx.claims = ((DefaultOidcUser) o).getClaims();
            } else if (o instanceof JwtAuthenticationToken) {
                ctx.claims = ((JwtAuthenticationToken) o).getToken().getClaims();
            }
            if (o instanceof Authentication) {
                ctx.groups = AccessControlService.getGroups((Authentication) o);
            }
        }
        return ctx;
    }
}
