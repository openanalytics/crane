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
package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

@Service
public class PathAccessControlService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SpecExpressionResolver specExpressionResolver;

    public PathAccessControlService(SpecExpressionResolver specExpressionResolver) {
        this.specExpressionResolver = specExpressionResolver;
    }

    public boolean canAccess(Authentication auth, String fullPath, PathComponent pathComponent) {
        Iterator<Path> path = Path.of(fullPath).iterator();
        path.next();
        return canAccess(auth, fullPath, pathComponent, path);
    }

    public boolean canAccess(Authentication auth, String fullPath, PathComponent pathComponent, Iterator<Path> path) {
        if (!canAccess(auth, pathComponent)) {
            logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), fullPath, pathComponent.getName());
            return false;
        }
        if (!pathComponent.hasPaths()) {
            logger.debug("User {} can access path {}, because they can access {}", auth.getName(), fullPath, pathComponent.getName());
            return true;
        }
        if (!path.hasNext()) {
            logger.debug("User {} can access path {}, because they can access {}", auth.getName(), fullPath, pathComponent.getName());
            return true;
        }

        String nextPartOfRequestedPath = path.next().toString();
        Optional<PathComponent> nextPathComponent = pathComponent.getPath(nextPartOfRequestedPath);

        if (nextPathComponent.isEmpty()) {
            logger.debug("User {} can access path {}, because there is no access-control for {}", auth.getName(), fullPath, nextPartOfRequestedPath);
            return true;
        }

        return canAccess(auth, fullPath, nextPathComponent.get(), path);
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
            if (CraneAccessControlService.isMember(auth, group)) {
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


    public boolean allowedByExpression(Authentication auth, PathComponent pathComponent) {
        if (!pathComponent.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), pathComponent);
        return specExpressionResolver.evaluateToBoolean(pathComponent.getAccessExpression(), context);
    }

}
