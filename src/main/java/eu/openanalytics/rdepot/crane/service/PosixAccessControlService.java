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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Iterator;
import java.util.Set;

@Service
public class PosixAccessControlService {
    private final CraneConfig config;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public PosixAccessControlService(CraneConfig config) {
        this.config = config;
    }

    public boolean canAccess(Authentication auth, String fullPath, Repository repository) {
        if (!config.isPosixAccessControl()) {
            return true;
        }

        if (auth == null || repository == null) {
            return false;
        }

        if (!pathSupportsPosix(repository.getStoragePath())) {
            logger.warn("File system is not posix compliant");
            return true;
        }

        Iterator<Path> subsequentPaths = Path.of(fullPath).iterator();
        String storageLocation = repository.getStorageLocation();
        StringBuilder pathBuilder = new StringBuilder(storageLocation.substring(0, storageLocation.length()-1));
        while (subsequentPaths.hasNext()) {
            String subDirectory = pathBuilder.append("/").toString();
            if (!canAccess(auth, subDirectory)) {
                logger.debug("User {} cannot access path {} because they cannot access {}", auth.getName(), fullPath, subDirectory);
                return false;
            }
            pathBuilder.append(subsequentPaths.next());
        }
        String completePath = pathBuilder.toString();
        return canAccess(auth, completePath);
    }

    private boolean pathSupportsPosix(Path storagePath) {
        return storagePath.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private boolean canAccess(Authentication auth, String path) {
        PosixFileAttributes attributes;
        try {
            attributes = Files.getFileAttributeView(Path.of(path), PosixFileAttributeView.class).readAttributes();
        } catch (IOException e) { // TODO: find a way to produce this warning
            logger.warn(String.format("Could not view POSIX file system permissions of %s", path), e);
            return false;
        }

        Set<PosixFilePermission> permissions = attributes.permissions();
        if (attributes.owner().getName().equalsIgnoreCase(auth.getName())) {
            return permissions.contains(PosixFilePermission.OWNER_READ);
        }
        if (CraneAccessControlService.isMember(auth, attributes.group().getName())) {
            return permissions.contains(PosixFilePermission.GROUP_READ);
        }
        return false;
    }
}
