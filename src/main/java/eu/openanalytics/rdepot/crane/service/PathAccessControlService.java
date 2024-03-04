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
import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.model.runtime.CraneDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

@Service
public class PathAccessControlService {

    private final AccessControlService accessControlService;
    private final CraneConfig craneConfig;
    private final UserService userService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PathAccessControlService(AccessControlService accessControlService, CraneConfig craneConfig, UserService userService) {
        this.accessControlService = accessControlService;
        this.craneConfig = craneConfig;
        this.userService = userService;
    }

    /**
     * Whether the provided user can access the path in the provided request.
     * Checks the request for path traversal.
     * @param auth the user
     * @param request the request to check
     * @return whether the user can access the path in the request
     */
    public boolean canAccess(Authentication auth, HttpServletRequest request) {
        if (auth == null || request == null) {
            return false;
        }

        String requestPath = request.getServletPath();
        if (requestPath == null || !checkPathSecurity(requestPath)) {
            return false;
        }
        try {
            Path path = Path.of(requestPath);

            Repository repository = craneConfig.getRepository(path.getName(0).toString());
            if (repository == null) {
                return false;
            }

            Iterator<Path> iterator = path.iterator();
            iterator.next();

            return canAccess(auth, requestPath, repository, iterator);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Whether the current user can access the provided directory.
     * @param repository the repository to which this directory belongs
     * @param craneDirectory the directory to check
     * @return whether the user can access
     */
    public boolean canAccess(Repository repository, CraneDirectory craneDirectory) {
        Path path = Path.of(craneDirectory.getPath());
        Iterator<Path> iterator = path.iterator();
        iterator.next();
        return canAccess(userService.getUser(), craneDirectory.getPath(), repository, iterator);
    }

    /**
     * Checks the path for path traversal.
     * @param path the path to check
     * @return whether the path can be trusted
     */
    private boolean checkPathSecurity(String path) {
        if (path.contains("%")) {
            // don't support encoded paths
            return false;
        }

        File absolutePath = new File(path);

        try {
            String canonicalPath = absolutePath.getCanonicalPath();
            if (!new File(canonicalPath).isAbsolute()) {
                return false;
            }
            if (!absolutePath.getAbsolutePath().equals(canonicalPath)) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private boolean canAccess(Authentication auth, String fullPath, PathComponent pathComponent, Iterator<Path> path) {
        if (!accessControlService.canAccess(auth, pathComponent)) {
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

}
