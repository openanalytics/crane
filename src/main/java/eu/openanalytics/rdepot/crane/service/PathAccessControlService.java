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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

@Service
public class PathAccessControlService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccessControlService accessControlService;

    public PathAccessControlService(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public boolean canAccess(Authentication auth, String fullPath, PathComponent pathComponent, Iterator<Path> path) {
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
