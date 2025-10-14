/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.model.runtime;

import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.upload.PathWriteAccessControlService;
import eu.openanalytics.crane.upload.PosixWriteAccessControlService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Optional;

public interface CraneResource {

    public static boolean isPathComponent(Path path, Repository repository) {
        Path relativePath = repository.getStoragePath().getParent().relativize(path);
        Iterator<Path> pathIterator = relativePath.iterator();
        pathIterator.next();
        Optional<PathComponent> parentPathComponent = Optional.of(repository);
        while (pathIterator.hasNext() && parentPathComponent.isPresent()) {
            Path nextPath = pathIterator.next();
            parentPathComponent = parentPathComponent.get().getPath(nextPath.toString());
        }
        return !pathIterator.hasNext() && parentPathComponent.isPresent();
    }

    public static CraneResource createFromPath(Path path, Repository repository, PathWriteAccessControlService pathWriteAccessControlService, PosixWriteAccessControlService posixWriteAccessControlService) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);

            if (attributes.isDirectory()) {
                boolean isPathComponent = isPathComponent(path, repository);
                String relativePath = "/" + repository.getStoragePath().getParent().relativize(path);
                String subPathString = repository.getStoragePath().relativize(path).toString();
                boolean hasWriteAccess = pathWriteAccessControlService.canAccess(repository, subPathString) && posixWriteAccessControlService.canAccess(repository, subPathString);
                return new CraneDirectory(path.getFileName().toString(), relativePath, isPathComponent, hasWriteAccess);
            } else {
                return new CraneFile(path.getFileName().toString(), attributes.lastModifiedTime().toInstant(), attributes.size());
            }
        } catch (IOException e) {
            return null;
        }
    }

    public String getName();

}
