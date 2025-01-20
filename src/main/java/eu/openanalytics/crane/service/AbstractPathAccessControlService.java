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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

public abstract class AbstractPathAccessControlService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final SpecExpressionResolver specExpressionResolver;
    protected final UserService userService;
    protected final CraneConfig craneConfig;

    public AbstractPathAccessControlService(SpecExpressionResolver specExpressionResolver, UserService userService, CraneConfig craneConfig) {
        this.specExpressionResolver = specExpressionResolver;
        this.userService = userService;
        this.craneConfig = craneConfig;
    }

    protected abstract AccessControl getAccessControl(PathComponent pathComponent);

    public boolean canAccess(String repository, String path) {
        return canAccess(craneConfig.getRepository(repository), path);
    }

    public boolean canAccess(Repository repository, String fullPath) {
        return canAccess(userService.getUser(), fullPath, repository, Path.of(fullPath).iterator());
    }

    public boolean canAccess(Authentication auth, String fullPath, PathComponent pathComponent, Iterator<Path> path) {
        String accessPath = pathComponent.getName() + fullPath;
        if (!canAccess(auth, getAccessControl(pathComponent))) {
            logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), accessPath, pathComponent.getName());
            return false;
        }
        if (!pathComponent.hasPaths()) {
            logger.debug("User {} can access path {}, because they can access {}", auth.getName(), accessPath, pathComponent.getName());
            return true;
        }
        if (!path.hasNext()) {
            logger.debug("User {} can access path {}, because they can access {}", auth.getName(), accessPath, pathComponent.getName());
            return true;
        }

        String nextPartOfRequestedPath = path.next().toString();
        Optional<PathComponent> nextPathComponent = pathComponent.getPath(nextPartOfRequestedPath);

        if (nextPathComponent.isEmpty()) {
            logger.debug("User {} can access path {}, because there is no access-control for {}", auth.getName(), accessPath, nextPartOfRequestedPath);
            return true;
        }

        return canAccess(auth, fullPath, nextPathComponent.get(), path);
    }

    public boolean canAccess(Authentication auth, AccessControl accessControl) {
        if (auth == null || accessControl == null) {
            return false;
        }

        if (accessControl.getPublic()) {
            return true;
        }

        if (allowedByNetwork(auth, accessControl)) {
            return true;
        }

        if (auth instanceof AnonymousAuthenticationToken) {
            // no anonymous users allowed beyond this stage
            return false;
        }

        if (accessControl.isAnyAuthenticatedUser()) {
            return true;
        }

        if (allowedByGroups(auth, accessControl)) {
            return true;
        }

        if (allowedByUsers(auth, accessControl)) {
            return true;
        }

        if (allowedByExpression(auth, accessControl)) {
            return true;
        }

        return false;
    }

    public boolean allowedByGroups(Authentication auth, AccessControl accessControl) {
        if (!accessControl.hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : accessControl.getGroups()) {
            if (userService.isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth,  AccessControl accessControl) {
        if (!accessControl.hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : accessControl.getUsers()) {
            if (auth.getName().equals(user)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByNetwork(Authentication auth, AccessControl accessControl) {
        if (!accessControl.hasNetworkAccess()) {
            // no ip address ranges defined -> this user has no access based on ip address
            return false;
        }
        WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();
        if (details == null) {
            return false;
        }
        for (IpAddressMatcher matcher : accessControl.getNetworkMatchers()) {
            if (matcher.matches(details.getRemoteAddress())) {
                return true;
            }
        }
        return false;
    }


    public boolean allowedByExpression(Authentication auth, AccessControl accessControl) {
        if (!accessControl.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), accessControl);
        return specExpressionResolver.evaluateToBoolean(accessControl.getExpression(), context);
    }


}
