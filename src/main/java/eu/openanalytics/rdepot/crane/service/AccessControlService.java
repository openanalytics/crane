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
package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccessControlService {

    private final SpecExpressionResolver specExpressionResolver;

    public AccessControlService(SpecExpressionResolver specExpressionResolver) {
        this.specExpressionResolver = specExpressionResolver;
    }

    public boolean canAccessRepository(Authentication auth, Repository repository) {
        if (auth == null || repository == null) {
            return false;
        }

        if (repository.getPublic()) {
            return true;
        }


        return canAccess(auth, repository);
    }

    public boolean canAccess(Authentication auth, PathComponent pathComponent) {
        if (auth == null || pathComponent == null) {
            return false;
        }

        if (pathComponent.getPublic()) {
            return true;
        }

        if (allowedByNetwork(auth, pathComponent)) {
            return true;
        }

        if (auth instanceof AnonymousAuthenticationToken) {
            // no anonymous users allowed beyond this stage
            return false;
        }

        if (pathComponent.isAccessAnyAuthenticatedUser()) {
            return true;
        }

        if (allowedByGroups(auth, pathComponent)) {
            return true;
        }

        if (allowedByUsers(auth, pathComponent)) {
            return true;
        }

        if (allowedByExpression(auth, pathComponent)) {
            return true;
        }

        return false;
    }

    public boolean allowedByGroups(Authentication auth, PathComponent PathComponent) {
        if (!PathComponent.hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : PathComponent.getAccessGroups()) {
            if (isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth, PathComponent PathComponent) {
        if (!PathComponent.hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : PathComponent.getAccessUsers()) {
            if (auth.getName().equals(user)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByNetwork(Authentication auth, PathComponent PathComponent) {
        if (!PathComponent.hasNetworkAccess()) {
            // no ip address ranges defined -> this user has no access based on ip address
            return false;
        }
        WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();
        if (details == null) {
            return false;
        }
        for (IpAddressMatcher matcher : PathComponent.getAccessNetworkMatchers()) {
            if (matcher.matches(details.getRemoteAddress())) {
                return true;
            }
        }
        return false;
    }


    public boolean allowedByExpression(Authentication auth, PathComponent PathComponent) {
        if (!PathComponent.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), PathComponent);
        return specExpressionResolver.evaluateToBoolean(PathComponent.getAccessExpression(), context);
    }

    public boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
            String groupName = grantedAuth.getAuthority().toUpperCase();
            if (groupName.startsWith("ROLE_")) {
                groupName = groupName.substring(5);
            }
            if (groupName.equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getGroups(Authentication auth) {
        List<String> groups = new ArrayList<>();
        if (auth != null) {
            for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
                String authName = grantedAuth.getAuthority().toUpperCase();
                if (authName.startsWith("ROLE_")) authName = authName.substring(5);
                groups.add(authName);
            }
        }
        return groups;
    }

}
