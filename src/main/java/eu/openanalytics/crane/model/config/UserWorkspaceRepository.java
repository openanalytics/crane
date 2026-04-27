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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UserWorkspaceRepository extends Repository {

    public UserWorkspaceRepository(String userHomeStorageLocation, Path userHomeStoragePath) throws IOException {
        super(null,
            null,
            userHomeStorageLocation,
            null,
            false,
            null,
            new AccessControl(null, null, false, null, true, null),
            new AccessControl(null, null, false, null, true, null),
            null,
            null,
            null,
            null);
        init(null, userHomeStoragePath);
    }

    private Path getPath() throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("Cannot read UserWorkspaceRepository without authentication");
        }
        Path path = super.getStoragePath().resolve(auth.getName());
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
        return path;
    }

    @Override
    public String getStorageLocation() {
        try {
            return getPath().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path getStoragePath() {
        try {
            return getPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "Workspace";
    }

}
