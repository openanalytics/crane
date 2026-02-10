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
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.util.FileSize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CranePath {

    @Getter
    private final Path storagePath;

    @Getter
    @EqualsAndHashCode.Include
    private final String pathRelativeToRepository;

    @Getter
    @EqualsAndHashCode.Include
    private final Repository repository;

    @Getter(lazy = true)
    private final PathComponent pathComponent = CranePathBuilder.getPathComponent(this).orElse(null);

    @Getter(lazy = true)
    private final boolean existsInConfig = getPathComponent() != null;

    @Getter(lazy = true)
    private final boolean directory = Files.isDirectory(storagePath);

    @Getter(lazy = true)
    private final CranePath indexFile = CranePathBuilder.getIndexFile(this);

    @Getter(lazy = true)
    private final boolean anIndexFile = getPathRelativeToRepository().endsWith(getRepository().getIndexFileName());

    @Getter(lazy = true)
    private final boolean exists = Files.exists(storagePath);

    @Getter(lazy = true)
    private final CranePath parent = CranePathBuilder.getParent(this);

    @Getter(lazy = true)
    private final MediaType mediaType = CranePathBuilder.getMediaType(this);

    @Getter(lazy = true)
    private final Stream<CranePath> contents = CranePathBuilder.getContents(this);

    // does not include context path
    @Getter(lazy = true)
    private final String webUri = "/" + getRepository().getName() + "/" + getPathRelativeToRepository();

    @Getter(lazy = true)
    private final String name = Path.of(getPathRelativeToRepository()).getFileName().toString();

    @Getter(lazy = true)
    private final List<CranePath> components = CranePathBuilder.getComponents(this);

    @Getter(lazy = true)
    private final AccessControl readAccess = CranePathBuilder.getReadAccess(this);

    @Getter(lazy = true)
    private final AccessControl writeAccess = CranePathBuilder.getWriteAccess(this);

    @Getter(lazy = true)
    private final BasicFileAttributes basicFileAttributes = CranePathBuilder.getBasicFileAttributes(this);

    @Getter(lazy = true)
    private final Instant lastModifiedTime = getBasicFileAttributes().lastModifiedTime().toInstant();

    @Getter(lazy = true)
    private final long lastModifiedTimeInMillis = getBasicFileAttributes().lastModifiedTime().toMillis();

    @Getter(lazy = true)
    private final Long size = getBasicFileAttributes().size();

    @Getter(lazy = true)
    private final String formattedSize = FileSize.bytesToHumanReadableBinary(getSize());

    @Getter(lazy = true)
    private final File storageFile = getStoragePath().toFile();

    public CranePath(Repository repository, String pathRelativeToRepository)  {
        this.repository = repository;
        // TODO normalize and validate (not start with / , end with / )
        if (pathRelativeToRepository.startsWith("/")) {
            this.pathRelativeToRepository = pathRelativeToRepository.substring(1);
        } else {
            this.pathRelativeToRepository = pathRelativeToRepository;
        }
        storagePath = repository.getStoragePath().resolve(this.pathRelativeToRepository);
    }

    public InputStream asInputStremam() throws IOException {
        if (isDirectory()) {
            throw new IllegalStateException("CranePath is a directory and cannot be opened as InputStream");
        }
        return Files.newInputStream(storagePath);
    }

    public Map<String, Object> readAttributes(String attributes) throws IOException {
        return Files.readAttributes(storagePath, attributes);
    }

    public void setAttribute(String attribute, Object value) throws IOException {
        Files.setAttribute(storagePath, attribute, value);
    }

    public void walkFileTree(FileVisitor<CranePath> visitor) throws IOException {
        if (!isDirectory()) {
            throw new IllegalStateException("CranePath is a file and cannot be walked");
        }
        Files.walkFileTree(storagePath, new FileVisitor<Path>() {

            @Override
            public @NotNull FileVisitResult preVisitDirectory(Path path, @NotNull BasicFileAttributes basicFileAttributes) throws IOException {
                return visitor.preVisitDirectory(repository.getPathFromStoragePath(path), basicFileAttributes);
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path path, @NotNull BasicFileAttributes basicFileAttributes) throws IOException {
                return visitor.visitFile(repository.getPathFromStoragePath(path), basicFileAttributes);
            }

            @Override
            public @NotNull FileVisitResult visitFileFailed(Path path, @NotNull IOException e) throws IOException {
                return visitor.visitFileFailed(repository.getPathFromStoragePath(path), e);
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(Path path, @Nullable IOException e) throws IOException {
                return visitor.postVisitDirectory(repository.getPathFromStoragePath(path), e);
            }

        });
    }

    public <V extends FileAttributeView> V getFileAttributeView(Class<V> posixFileAttributeViewClass) {
        return Files.getFileAttributeView(storagePath, posixFileAttributeViewClass);
    }

    @Override
    public String toString() {
        return "CranePath{" +
            "storagePath=" + getStoragePath() +
            ", webUri='" + getWebUri() + '\'' +
            '}';
    }

}
