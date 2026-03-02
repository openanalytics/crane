/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
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
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.security.CraneUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractPosixAccessControlService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final UserService userService;
    protected final CraneConfig craneConfig;

    protected AbstractPosixAccessControlService(UserService userService, CraneConfig craneConfig) {
        this.userService = userService;
        this.craneConfig = craneConfig;
    }

    protected abstract PosixFilePermission getOwnerAccess();

    protected abstract PosixFilePermission getGroupAccess();

    public boolean canAccess(CranePath path) {
        return canAccess(userService.getUser(), path);
    }

    public boolean canAccess(Authentication auth, CranePath path) {
        if (auth == null) {
            return false;
        }

        if (!path.getRepository().isPosixAccessControl()) {
            return true;
        }

        if (!pathSupportsPosix(path.getRepository().getStoragePath())) {
            logger.warn("File system is not posix compliant");
            return true;
        }

        List<CranePath> pathComponents = path.getComponents();
        if (pathComponents.isEmpty()) {
            // request is accessing repository, but there is no posix check for the repository itself
            return true;
        }
        CranePath lastPathComponent = pathComponents.remove(pathComponents.size() - 1);
        for (CranePath subsequentPath : pathComponents) {
            if (!canAccessPosix(auth, subsequentPath)) {
                logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), path.getWebUri(), subsequentPath);
                return false;
            }
        }
        // only check permissions of last component if the path actually exists
        // i.e. when uploading the file does not exist and we don't check the permission
        if (lastPathComponent.isExists() && !canAccessPosix(auth, lastPathComponent)) {
            logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), path.getWebUri(), lastPathComponent);
            return false;
        }
        return true;
    }

    protected boolean pathSupportsPosix(Path storagePath) {
        return storagePath.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    protected boolean canAccessPosix(Authentication auth, CranePath cranePath) {
        if (auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        CraneUser craneUser = (CraneUser) auth.getPrincipal();
        PosixFileAttributes attributes;
        int pathUID, pathGID;
        try {
            Map<String, Object> pathAttributes = cranePath.readAttributes("unix:uid,gid");
            pathUID = (int) pathAttributes.get("uid");
            pathGID = (int) pathAttributes.get("gid");
            attributes = cranePath.getFileAttributeView(PosixFileAttributeView.class).readAttributes();
        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            logger.warn("Could not view POSIX file system permissions of {}", cranePath, e);
            return false;
        }

        Set<PosixFilePermission> permissions = attributes.permissions();
        int userUID = craneUser.getPosixUID();
        if (attributes.owner().getName().equalsIgnoreCase(auth.getName()) || pathUID == userUID) {
            return permissions.contains(getOwnerAccess());
        }

        if (userService.isMember(auth, attributes.group().getName()) || craneUser.getPosixGIDs().contains(pathGID)) {
            return permissions.contains(getGroupAccess());
        }
        return false;
    }

}
