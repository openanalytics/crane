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

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.model.runtime.CraneDirectory;
import eu.openanalytics.rdepot.crane.model.runtime.CraneFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

@Service
public class CraneAccessControlService {

    private final PosixAccessControlService posixAccessControlService;
    private final PathAccessControlService pathAccessControlService;
    private final CraneConfig craneConfig;
    private final UserService userService;

    public CraneAccessControlService(PosixAccessControlService posixAccessControlService, PathAccessControlService pathAccessControlService, CraneConfig craneConfig, UserService userService) {
        this.posixAccessControlService = posixAccessControlService;
        this.pathAccessControlService = pathAccessControlService;
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

    public boolean canAccessFile(Repository repository, CraneFile craneFile) {
        return posixAccessControlService.canAccess(userService.getUser(), repository.getName() + "/" + craneFile.getName(), repository);
    }
    private boolean canAccess(Authentication auth, String fullPath, Repository repository, Iterator<Path> path) {
        return posixAccessControlService.canAccess(auth, fullPath, repository) || pathAccessControlService.canAccess(auth, fullPath, repository, path);
    }

}
