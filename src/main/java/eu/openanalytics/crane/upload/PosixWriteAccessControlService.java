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
package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.service.AbstractPosixAccessControlService;
import eu.openanalytics.crane.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Iterator;

@Service
public class PosixWriteAccessControlService extends AbstractPosixAccessControlService {
    public PosixWriteAccessControlService(UserService userService, CraneConfig craneConfig) {
        super(userService, craneConfig);
    }

    @Override
    protected PosixFilePermission getOwnerAccess() {
        return PosixFilePermission.OWNER_WRITE;
    }

    @Override
    protected PosixFilePermission getGroupAccess() {
        return PosixFilePermission.GROUP_WRITE;
    }

    @Override
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
        return canAccessPosix(auth, completePath.substring(0, completePath.lastIndexOf("/") + 1), repository);
    }

}
