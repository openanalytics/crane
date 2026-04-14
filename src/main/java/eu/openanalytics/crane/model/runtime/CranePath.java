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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;

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

    /**
     * The full, physical path on the storage.
     */
    @Getter
    private final Path storagePath;

    /**
     * Path relative to the repository.
     * Does never start with / .
     * Does never end with / .
     */
    @Getter
    @EqualsAndHashCode.Include
    private final String pathRelativeToRepository;

    /**
     * The repository owning this path.
     */
    @Getter
    @EqualsAndHashCode.Include
    private final Repository repository;

    private final BasicFileAttributes preLoadedBasicFileAttributes;

    /**
     * The {@link PathComponent} (coming from the Crane config) corresponding to this path.
     * Returns null when path is not configured in the Crane config.
     */
    @Getter(lazy = true)
    private final PathComponent pathComponent = CranePathBuilder.getPathComponent(this).orElse(null);

    /**
     * Whether this path exists as a {@link PathComponent} in the Crane config.
     */
    @Getter(lazy = true)
    private final boolean existsInConfig = CranePathBuilder.existsInConfig(this);

    /**
     * Whether this path is a directory (checked by querying the storage).
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     * Returns false if the directory does not exist.
     */
    @Getter(lazy = true)
    private final boolean directory = CranePathBuilder.isDirectory(this);

    /**
     * Gets the path to the index file.
     */
    @Getter(lazy = true)
    private final CranePath indexFilePath = CranePathBuilder.getIndexFilePath(this);

    /**
     * Whether this path is an index file.
     */
    @Getter(lazy = true)
    private final boolean indexFile = getName().equals(repository.getIndexFileName());

    /**
     * Whether this path is a metadata file.
     */
    @Getter(lazy = true)
    private final boolean metadataFile = getParent() != null && getParent().getName().equals(repository.getMetadataDirectoryName());

    /**
     * Whether this path is a metadata directory.
     */
    @Getter(lazy = true)
    private final boolean metadataDirectory = getName().equals(repository.getMetadataDirectoryName());

    /**
     * Whether this path is a metadata directory or file.
     */
    @Getter(lazy = true)
    private final boolean metadataDirectoryOrFile = isMetadataFile() || isMetadataDirectory();

    /**
     * Whether this path is a metadata file.
     */
    @Getter(lazy = true)
    private final boolean metadataSchemaFile = isMetadataFile() && getName().equals(repository.getMetadataSchemaFileName());

    /**
     * Whether this path is an invalid metadata path, i.e. directories nested in a metadata directory.
     */
    @Getter(lazy = true)
    private final boolean invalidMetadataPath = CranePathBuilder.isInvalidMetadataPath(this);

    /**
     * Whether this path exists (checked by querying the storage).
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     */
    @Getter(lazy = true)
    private final boolean exists = CranePathBuilder.exists(this);

    /**
     * Gets the parent path. Returns null if already at root of repository.
     */
    @Getter(lazy = true)
    private final CranePath parent = CranePathBuilder.getParent(this);

    /**
     * Gets the media type of the file.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    private final MediaType mediaType = CranePathBuilder.getMediaType(this);

    /**
     * Gets the contents of this directory.
     */
    @Getter(lazy = true)
    private final Stream<CranePath> contents = CranePathBuilder.getContents(this);

    /**
     * Gets how this path is accessed through the web API (without any context-path).
     */
    @Getter(lazy = true)
    private final String webUri = "/" + getRepository().getName() + "/" + getPathRelativeToRepository();

    /**
     * Gets the name of this directory or file.
     */
    @Getter(lazy = true)
    private final String name = getStoragePath().getFileName().toString();

    /**
     * Gets all components (i.e. all parent paths) of this path.
     * The path "test_path/my/complex/path" returns:
     * - CranePath(repository, "test_path"),
     * - CranePath(repository, "test_path/my"),
     * - CranePath(repository, "test_path/my/complex"),
     * - CranePath(repository, "test_path/my/complex/path")
     */
    @Getter(lazy = true)
    private final List<CranePath> components = CranePathBuilder.getComponents(this);

    /**
     * Gets the read access of the {@link PathComponent} (coming from the Crane config) corresponding to this path.
     * Returns null when path is not configured in the Crane config.
     */
    @Getter(lazy = true)
    private final AccessControl readAccess = CranePathBuilder.getReadAccess(this);

    /**
     * Gets the write access of the {@link PathComponent} (coming from the Crane config) corresponding to this path.
     * Returns null when path is not configured in the Crane config.
     */
    @Getter(lazy = true)
    private final AccessControl writeAccess = CranePathBuilder.getWriteAccess(this);

    /**
     * Gets the read access to metadata files in the {@link PathComponent} (coming from the Crane config) corresponding to this path.
     * Returns null when path is not configured in the Crane config.
     */
    @Getter(lazy = true)
    private final AccessControl metadataReadAccess = CranePathBuilder.getMetadataReadAccess(this);

    /**
     * Gets the write access to metadata files in the {@link PathComponent} (coming from the Crane config) corresponding to this path.
     * Returns null when path is not configured in the Crane config.
     */
    @Getter(lazy = true)
    private final AccessControl metadataWriteAccess = CranePathBuilder.getMetadataWriteAccess(this);

    /**
     * Wrapper for {@link Files#readAttributes}.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    @Nullable
    private final BasicFileAttributes basicFileAttributes = preLoadedBasicFileAttributes != null ? preLoadedBasicFileAttributes : CranePathBuilder.getBasicFileAttributes(this);

    /**
     * Gets the last modified timestamp of this path.
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    private final Instant lastModifiedTime = CranePathBuilder.getLastModifiedTime(this);

    /**
     * Gets the last modified timestamp of this path (in milliseconds).
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    private final Long lastModifiedTimeInMillis = CranePathBuilder.getLastModifiedTimeMillis(this);

    /**
     * Gets the size of this path.
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    private final Long size = CranePathBuilder.getSize(this);

    /**
     * Gets a formatted, human-readable representation of the size of this path.
     * Uses the cached result from {@link CranePath#basicFileAttributes } to improve performance.
     * Returns null if the file or directory doesn't exist.
     */
    @Getter(lazy = true)
    private final String formattedSize = CranePathBuilder.getFormattedSize(this);

    /**
     * Get a unique id for this file.
     * This is the SHA-256 hash of the {@link webUri} property.
     */
    @Getter(lazy = true)
    private final String id = CranePathBuilder.getId(this);

    public CranePath(Repository repository, String pathRelativeToRepository) {
        this.repository = repository;
        this.pathRelativeToRepository = Strings.CS.removeStart(Strings.CS.removeEnd(pathRelativeToRepository, "/"), "/");
        this.storagePath = repository.getStoragePath().resolve(this.pathRelativeToRepository);
        this.preLoadedBasicFileAttributes = null;
    }

    public CranePath(Repository repository, String pathRelativeToRepository, Path path) {
        this.repository = repository;
        this.pathRelativeToRepository = Strings.CS.removeStart(Strings.CS.removeEnd(pathRelativeToRepository, "/"), "/");
        this.storagePath = path;
        this.preLoadedBasicFileAttributes = null;
    }

    public CranePath(Repository repository, String pathRelativeToRepository, Path path, BasicFileAttributes basicFileAttributes) {
        this.repository = repository;
        this.pathRelativeToRepository = Strings.CS.removeStart(Strings.CS.removeEnd(pathRelativeToRepository, "/"), "/");
        this.storagePath = path;
        this.preLoadedBasicFileAttributes = basicFileAttributes;
    }

    public CranePath(CranePath cranePath) {
        this(cranePath.getRepository(), cranePath.getPathRelativeToRepository());
    }

    /**
     * Gets a sub-path for this path.
     *
     * @param pathRelativeToThisPath the sub-path to create
     * @return the sub-path
     */
    public CranePath getPath(String pathRelativeToThisPath) {
        pathRelativeToThisPath = Strings.CS.removeStart(pathRelativeToThisPath, "/");
        return new CranePath(repository, pathRelativeToRepository + "/" + pathRelativeToThisPath);
    }

    /**
     * Gets a sub-path for this path. Re-uses existing {@link Path} object for performance.
     *
     * @param pathRelativeToThisPath the sub-path to create
     * @param path                   existing path object
     * @return the sub-path
     */
    public CranePath getPath(String pathRelativeToThisPath, Path path) {
        pathRelativeToThisPath = Strings.CS.removeStart(pathRelativeToThisPath, "/");
        return new CranePath(repository, pathRelativeToRepository + "/" + pathRelativeToThisPath, path);
    }

    /**
     * Returns the file as an {@link InputStream}
     *
     * @return the file as an {@link InputStream}
     * @throws IOException if an I/O error occurs
     */
    public InputStream asInputStream() throws IOException {
        if (isDirectory()) {
            throw new IllegalStateException("CranePath is a directory and cannot be opened as InputStream");
        }
        return Files.newInputStream(storagePath);
    }

    /**
     * Wrapper for {@link Files#readAttributes}.
     *
     * @param attributes the attributes to read
     * @return map of attributes
     * @throws IOException if an I/O error occurs
     */
    public Map<String, Object> readAttributes(String attributes) throws IOException {
        return Files.readAttributes(storagePath, attributes);
    }

    /**
     * Wrapper for {@link Files#setAttribute}.
     *
     * @param attribute the attribute to set
     * @param value     the attribute value
     * @throws IOException if an I/O error occurs
     */
    public void setAttribute(String attribute, Object value) throws IOException {
        Files.setAttribute(storagePath, attribute, value);
    }

    /**
     * Wrapper for {@link Files#walkFileTree} using {@link FileVisitor<CranePath>}.
     *
     * @param visitor visitor to use while walking the path
     * @throws IOException if an I/O error occurs
     */
    public void walkFileTree(FileVisitor<CranePath> visitor) throws IOException {
        if (!isDirectory()) {
            throw new IllegalStateException("CranePath is a file and cannot be walked");
        }
        Files.walkFileTree(storagePath, new FileVisitor<>() {

            @Override
            public @NotNull FileVisitResult preVisitDirectory(Path path, @NotNull BasicFileAttributes basicFileAttributes) throws IOException {
                return visitor.preVisitDirectory(repository.getPathFromStoragePath(path, basicFileAttributes), basicFileAttributes);
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path path, @NotNull BasicFileAttributes basicFileAttributes) throws IOException {
                return visitor.visitFile(repository.getPathFromStoragePath(path, basicFileAttributes), basicFileAttributes);
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

    /**
     * Wrapper for {@link Files#getFileAttributeView}.
     *
     * @param posixFileAttributeViewClass the Class object corresponding to the file attribute view
     * @param <V>                         The {@link FileAttributeView} type
     * @return a file attribute view of the specified type, or null if the attribute view type is not available
     */
    public <V extends FileAttributeView> V getFileAttributeView(Class<V> posixFileAttributeViewClass) {
        return Files.getFileAttributeView(storagePath, posixFileAttributeViewClass);
    }

    @Override
    public String toString() {
        return "CranePath{" +
            "storagePath=" + getStoragePath() +
            ", pathRelativeToRepository='" + getWebUri() + '\'' +
            '}';
    }

}
