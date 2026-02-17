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
package eu.openanalytics.crane.model.runtime;

import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.PathComponent;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CranePathBuilder {

    private final static Tika tika = new Tika();

    public static Optional<PathComponent> getPathComponent(CranePath cranePath) {
        Optional<PathComponent> pathComponent = Optional.ofNullable(cranePath.getRepository());
        for (CranePath part : cranePath.getComponents()) {
            pathComponent = pathComponent.get().getPathComponent(part.getName());
            if (pathComponent.isEmpty()) {
                return Optional.empty();
            }
        }
        return pathComponent;
    }

    public static CranePath getIndexFile(CranePath cranePath) {
        if (!cranePath.isDirectory()) {
            throw new IllegalStateException("Not a directory");
        }
        return cranePath.getPath(cranePath.getRepository().getIndexFileName());
    }

    public static CranePath getParent(CranePath cranePath) {
        if (cranePath.getPathRelativeToRepository().isEmpty()) {
            return null;
        }
        Path parent = cranePath.getStoragePath().getParent();
        if (parent == null) {
            return new CranePath(cranePath.getRepository(), "");
        }
        return cranePath.getRepository().getPathFromStoragePath(parent);
    }

    public static MediaType getMediaType(CranePath cranePath) {
        if (cranePath.isDirectory()) {
            throw new IllegalStateException("CranePath is a directory and has no MediaType");
        }
        String mimeType = tika.detect(cranePath.getName());
        return MediaType.valueOf(mimeType);
    }

    @SuppressWarnings("resource")
    public static Stream<CranePath> getContents(CranePath cranePath)  {
        if (!cranePath.isDirectory()) {
            throw new IllegalStateException("CranePath must be a directory to list its content");
        }
        try {
            return Files
                .list(cranePath.getStoragePath())
                .map(f -> cranePath.getPath(f.getFileName().toString(), f));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<CranePath> getComponents(CranePath cranePath) {
        List<CranePath> components = new ArrayList<>();
        CranePath current = cranePath;
        while (current != null && current.getParent() != null) {
            components.add(0, current);
            current = current.getParent();
        }
        return components;
    }

    public static AccessControl getWriteAccess(CranePath cranePath) {
        PathComponent res = cranePath.getPathComponent();
        if (res == null) {
            return null;
        }
        return res.getWriteAccess();
    }

    public static AccessControl getReadAccess(CranePath cranePath) {
        PathComponent res = cranePath.getPathComponent();
        if (res == null) {
            return null;
        }
        return res.getReadAccess();
    }

    public static BasicFileAttributes getBasicFileAttributes(CranePath cranePath) {
        try {
            return Files.readAttributes(cranePath.getStoragePath(), BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
