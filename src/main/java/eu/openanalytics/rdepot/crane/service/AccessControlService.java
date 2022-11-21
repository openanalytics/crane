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

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.Repository;
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

    private final CraneConfig craneConfig;
    private final SpecExpressionResolver specExpressionResolver;

    public AccessControlService(CraneConfig craneConfig, SpecExpressionResolver specExpressionResolver) {
        this.craneConfig = craneConfig;
        this.specExpressionResolver = specExpressionResolver;
    }

    public boolean canAccess(Authentication auth, String repositoryName) {
        if (auth == null || repositoryName == null) {
            return false;
        }

        Repository repository = craneConfig.getRepository(repositoryName);
        if (repository == null) {
            return false;
        }

        return canAccess(auth, repository);
    }

    public boolean canAccess(Authentication auth, Repository repository) {
        if (auth == null || repository == null) {
            return false;
        }

        if (repository.getPublic()) {
            return true;
        }

        if (allowedByNetwork(auth, repository)) {
            return true;
        }

        if (auth instanceof AnonymousAuthenticationToken) {
            // no anonymous users allowed beyond this stage
            return false;
        }

        if (repository.isAccessAnyAuthenticatedUser()) {
            return true;
        }

        if (allowedByGroups(auth, repository)) {
            return true;
        }

        if (allowedByUsers(auth, repository)) {
            return true;
        }

        if (allowedByExpression(auth, repository)) {
            return true;
        }

        return false;
    }

    public boolean allowedByGroups(Authentication auth, Repository spec) {
        if (!spec.hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : spec.getAccessGroups()) {
            if (isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth, Repository repository) {
        if (!repository.hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : repository.getAccessUsers()) {
            if (auth.getName().equals(user)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByNetwork(Authentication auth, Repository repository) {
        if (!repository.hasNetworkAccess()) {
            // no ip address ranges defined -> this user has no access based on ip address
            return false;
        }
        WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();
        if (details == null) {
            return false;
        }
        for (IpAddressMatcher matcher : repository.getAccessNetworkMatchers()) {
            if (matcher.matches(details.getRemoteAddress())) {
                return true;
            }
        }
        return false;
    }


    public boolean allowedByExpression(Authentication auth, Repository repository) {
        if (!repository.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), repository);
        return specExpressionResolver.evaluateToBoolean(repository.getAccessExpression(), context);
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
