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
import eu.openanalytics.crane.util.FileSize;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
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

    public static CranePath getIndexFilePath(CranePath cranePath) {
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
        if (!cranePath.isExists()) {
            return null;
        }
        String mimeType = tika.detect(cranePath.getName());
        return MediaType.valueOf(mimeType);
    }

    @SuppressWarnings("resource")
    public static Stream<CranePath> getContents(CranePath cranePath) {
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

    public static @Nullable BasicFileAttributes getBasicFileAttributes(CranePath cranePath) {
        try {
            return Files.readAttributes(cranePath.getStoragePath(), BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDirectory(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        if (basicFileAttributes == null) {
            return false;
        }
        return basicFileAttributes.isDirectory();
    }

    public static boolean exists(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        return basicFileAttributes != null;
    }

    public static Instant getLastModifiedTime(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        if (basicFileAttributes == null) {
            return null;
        }
        return basicFileAttributes.lastModifiedTime().toInstant();
    }

    public static Long getLastModifiedTimeMillis(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        if (basicFileAttributes == null) {
            return null;
        }
        return basicFileAttributes.lastModifiedTime().toMillis();
    }

    public static Long getSize(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        if (basicFileAttributes == null) {
            return null;
        }
        return basicFileAttributes.size();
    }

    public static String getFormattedSize(CranePath cranePath) {
        BasicFileAttributes basicFileAttributes = cranePath.getBasicFileAttributes();
        if (basicFileAttributes == null) {
            return null;
        }
        return FileSize.bytesToHumanReadableBinary(basicFileAttributes.size());
    }

    public static AccessControl getMetadataReadAccess(CranePath cranePath) {
        if (cranePath.isMetadataDirectory()) {
            // if this is the metadata directory or a specific metadata file, use the parent folder to check the permission
            cranePath = cranePath.getParent();
        }
        PathComponent res = cranePath.getPathComponent();
        if (res == null) {
            return null;
        }
        return res.getMetadataReadAccess();
    }

    public static AccessControl getMetadataWriteAccess(CranePath cranePath) {
        if (cranePath.isMetadataDirectory()) {
            // if this is the metadata directory or a specific metadata file, use the parent folder to check the permission
            cranePath = cranePath.getParent();
        }
        PathComponent res = cranePath.getPathComponent();
        if (res == null) {
            return null;
        }
        return res.getMetadataWriteAccess();
    }

    public static boolean existsInConfig(CranePath cranePath) {
        if (cranePath.isMetadataDirectory()) {
            return cranePath.getParent().isExistsInConfig();
        }
        return cranePath.getPathComponent() != null;
    }

    public static boolean isInvalidMetadataPath(CranePath cranePath) {
        if (cranePath.isMetadataDirectoryOrFile()) {
            return false;
        }
        CranePath parent = cranePath.getParent();
        while (parent != null) {
            if (parent.isMetadataDirectory()) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

}
