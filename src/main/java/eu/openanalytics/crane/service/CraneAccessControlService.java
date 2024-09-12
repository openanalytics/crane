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
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.upload.PathWriteAccessControlService;
import eu.openanalytics.crane.upload.PosixWriteAccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class CraneAccessControlService {

    private final PosixAccessControlService posixAccessControlService;
    private final PathAccessControlService pathAccessControlService;
    private final CraneConfig craneConfig;
    private final UserService userService;
    private final PathWriteAccessControlService pathWriteAccessControlService;
    private final PosixWriteAccessControlService posixWriteAccessControlService;

    public CraneAccessControlService(PosixAccessControlService posixAccessControlService, PathAccessControlService pathAccessControlService, CraneConfig craneConfig, UserService userService, PathWriteAccessControlService pathWriteAccessControlService, PosixWriteAccessControlService posixWriteAccessControlService) {
        this.posixAccessControlService = posixAccessControlService;
        this.pathAccessControlService = pathAccessControlService;
        this.craneConfig = craneConfig;
        this.userService = userService;
        this.pathWriteAccessControlService = pathWriteAccessControlService;
        this.posixWriteAccessControlService = posixWriteAccessControlService;
    }

    public static boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuth : auth.getAuthorities()) {
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
            for (GrantedAuthority grantedAuth : auth.getAuthorities()) {
                String authName = grantedAuth.getAuthority().toUpperCase();
                if (authName.startsWith("ROLE_")) authName = authName.substring(5);
                groups.add(authName);
            }
        }
        return groups;
    }

    /**
     * Whether the provided user can access the path in the provided request.
     * Checks the request for path traversal.
     *
     * @param auth    the user
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
            if (request.getMethod().equalsIgnoreCase("POST")) {
                return canWrite(auth, requestPath, repository);
            } else {
                return canAccess(auth, requestPath, repository);
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    public boolean canAccess(Authentication auth, Repository repository) {
        String fullUrlPath = "/" + repository.getName();
        return canAccess(auth, fullUrlPath, repository);
    }

    /**
     * Whether the current user can access the provided directory.
     *
     * @param repository the repository to which this directory belongs
     * @param path       the path to check
     * @return whether the user can access
     */
    public boolean canAccess(Repository repository, String path) {
        return canAccess(userService.getUser(), path, repository);
    }

    /**
     * Checks the path for path traversal.
     *
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

    private boolean canWrite(Authentication auth, String fullPath, Repository repository) {
        return pathWriteAccessControlService.canAccess(auth, fullPath, repository) && posixWriteAccessControlService.canAccess(auth, fullPath, repository);
    }
    
    private boolean canAccess(Authentication auth, String fullPath, Repository repository) {
        return pathAccessControlService.canAccess(auth, fullPath, repository) && posixAccessControlService.canAccess(auth, fullPath, repository);
    }

}
