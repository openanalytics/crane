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
package eu.openanalytics.crane.model.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;

public class UserWorkspaceRepository extends Repository {

    public UserWorkspaceRepository(String userHomeStorageLocation, Path userHomeStoragePath) {
        super(null,
            null,
            userHomeStorageLocation,
            null,
            false,
            null,
            new AccessControl(null, null, false, null, true, null),
            new AccessControl(null, null, false, null, true, null),
            null);
        init(null, userHomeStoragePath);
    }

    private Path getPath() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("Cannot read UserWorkspaceRepository without authentication");
        }
        Path path = super.getStoragePath().resolve(auth.getName());
        if (!path.toFile().isDirectory()) {
            createDirectory(path);
        }
        return path;
    }

    @Override
    public String getStorageLocation() {
        return getPath().toString();
    }

    @Override
    public Path getStoragePath() {
        return getPath();
    }

    @Override
    public boolean usesS3() {
        return false; // TODO #35659
    }
}
