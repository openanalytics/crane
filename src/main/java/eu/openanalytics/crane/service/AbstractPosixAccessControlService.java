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
import eu.openanalytics.crane.security.CraneUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Iterator;
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

    protected PosixFilePermission getOwnerAccess() {
        return null;
    }

    protected PosixFilePermission getGroupAccess() {
        return null;
    }

    public boolean canAccess(HttpServletRequest request) {
        Authentication auth = userService.getUser();
        Repository repository = craneConfig.getRepository(request);
        String relativePath = request.getRequestURI().replaceFirst("/__file", "");
        return canAccess(auth, relativePath, repository);
    }

    public boolean canAccess(Authentication auth, String fullPath, Repository repository) {
        if (auth == null || repository == null) {
            return false;
        }

        if (!repository.hasPosixAccessControl()) {
            return true;
        }

        if (!pathSupportsPosix(repository.getStoragePath())) {
            logger.warn("File system is not posix compliant");
            return true;
        }

        Iterator<Path> subsequentPaths = Path.of(fullPath).iterator();
        String storageLocation = repository.getStorageLocation();
        StringBuilder pathBuilder = new StringBuilder(storageLocation.substring(0, storageLocation.length() - 1));
        while (subsequentPaths.hasNext()) {
            String subDirectory = pathBuilder.append("/").toString();
            if (!canAccessPosix(auth, subDirectory, repository)) {
                logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), fullPath, subDirectory);
                return false;
            }
            pathBuilder.append(subsequentPaths.next());
        }
        String completePath = pathBuilder.toString();
        return canAccessPosix(auth, completePath, repository);
    }

    protected boolean pathSupportsPosix(Path storagePath) {
        return storagePath.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    public boolean canAccess(Repository repository) {
        return canAccess(repository, "/" + repository.getName());
    }

    public boolean canAccess(Repository repository, String stringPath) {
        return canAccess(userService.getUser(), stringPath, repository);
    }

    protected boolean canAccessPosix(Authentication auth, String stringPath, Repository repository) {
        CraneUser craneUser = (CraneUser) auth.getPrincipal();
        PosixFileAttributes attributes;
        int pathUID, pathGID;
        Path path = Path.of(stringPath);
        try {
            Map<String, Object> pathAttributes = Files.readAttributes(path, "unix:uid,gid");
            pathUID = (int) pathAttributes.get("uid");
            pathGID = (int) pathAttributes.get("gid");
            attributes = Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes();
        } catch (NoSuchFileException e) {
            return repository.getIndexFileName().equals(stringPath.replaceFirst("^.+/", ""));
        } catch (IOException e) {
            logger.warn("Could not view POSIX file system permissions of {}", path, e);
            return false;
        }

        Set<PosixFilePermission> permissions = attributes.permissions();
        int userUID = craneUser.getPosixUID();
        if (attributes.owner().getName().equalsIgnoreCase(auth.getName()) || pathUID == userUID) {
            return permissions.contains(getOwnerAccess());
        }

        if (CraneAccessControlService.isMember(auth, attributes.group().getName()) || craneUser.getPosixGIDs().contains(pathGID)) {
            return permissions.contains(getGroupAccess());
        }
        return false;
    }
}
