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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.util.EnvironmentUtils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Repository extends PathComponent {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String storageLocation;
    private boolean initialized = false;

    @Getter
    private final boolean posixAccessControl;

    @Getter
    private Path storagePath;

    @Getter
    private List<CacheRule> cache;

    @Getter
    private final String indexFileName;

    @Getter
    private final String metadataDirectoryName = "metadata";

    @Getter
    private final String metadataSchemaFileName = "schema.json";

    @Getter
    private final String extractedMetadataDirectoryName = "extracted-metadata";

    @Getter
    private final String onErrorExpression;

    @Getter
    private final List<RewriteRule> rewrites;

    @Getter
    private final byte[] metadataSchema;

    @ConstructorBinding
    public Repository(String indexFileName,
                      List<CacheRule> cache,
                      String storageLocation,
                      String onErrorExpression,
                      boolean posixAccessControl,
                      List<RewriteRule> rewrites,
                      AccessControl readAccess,
                      AccessControl writeAccess,
                      AccessControl metadataReadAccess,
                      AccessControl metadataWriteAccess,
                      Map<String, PathComponent> paths,
                      Map<String, Object> metadataSchema) {
        super(readAccess, writeAccess, metadataReadAccess, metadataWriteAccess, paths);
        this.indexFileName = Objects.requireNonNullElse(indexFileName, "index.html");
        this.cache = cache;
        this.storageLocation = storageLocation;
        this.onErrorExpression = onErrorExpression;
        this.posixAccessControl = posixAccessControl;
        this.rewrites = rewrites;
        this.metadataSchema = convertMetadataSchema(metadataSchema);
    }

    public void setCache(List<CacheRule> cache) {
        if (this.cache != null) {
            throw new IllegalStateException("Cache already set");
        }
        this.cache = cache;
    }

    public void validateRepositoryName() {
        List<String> invalidRepositoryNames = List.of(
            // OIDC related urls
            "logout", "login",
            // RequestMappers
            "logout-success", "error",
            // other urls
            "actuator", "favicon.ico", ".well-known", "Workspace"
        );
        if (invalidRepositoryNames.contains(getName()) || getName().startsWith("__")) {
            throw new IllegalArgumentException(
                String.format(
                    "Repository name %s is invalid, repository names should not start with '__' and should be different from: %s",
                    getName(), invalidRepositoryNames.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "))));
        }
    }

    public void init(List<CacheRule> defaultCache, Path storagePath) throws IOException {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }
        if (defaultCache != null && cache == null) {
            cache = defaultCache;
        }
        this.storagePath = storagePath;
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
            if (isPosixAccessControl()) {
                logger.warn("Repository '{}' was created no posix access control configured", getName());
            }
        }
        if (hasPaths()) {
            for (PathComponent pathComponent : getPaths()) {
                pathComponent.init(storagePath);
            }
        }
        initialized = true;
    }

    public void validate(boolean onlyPublic) {
        super.validate(onlyPublic);

        validateRepositoryName();

        AccessControl readAccess = getReadAccess();
        if (readAccess.isPublic() && (readAccess.hasGroupAccess() || readAccess.hasUserAccess() || readAccess.hasExpressionAccess() || readAccess.hasNetworkAccess() || isPosixAccessControl())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add read access control properties to a public repo", getName()));
        }

        AccessControl writeAccess = getWriteAccess();
        if (writeAccess.isPublic() && (writeAccess.hasGroupAccess() || writeAccess.hasUserAccess() || writeAccess.hasExpressionAccess() || writeAccess.hasNetworkAccess() || isPosixAccessControl())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add write access control properties to a public repo", getName()));
        }
    }

    public String getStorageLocation() {
        if (initialized) {
            throw new IllegalStateException("Repository is already initialized, use getStoragePath");
        }
        return storageLocation;
    }

    public CranePath getPath(String pathRelativeToRepository) {
        return new CranePath(this, pathRelativeToRepository);
    }

    public CranePath getPathFromStoragePath(Path fullStoragePath, BasicFileAttributes basicFileAttributes) {
        return new CranePath(this, getStoragePath().relativize(fullStoragePath).toString(), fullStoragePath, basicFileAttributes);
    }

    public CranePath getPathFromStoragePath(Path fullStoragePath) {
        return new CranePath(this, getStoragePath().relativize(fullStoragePath).toString(), fullStoragePath);
    }

    @Override
    public String toString() {
        return "Repository{" +
            "name='" + getName() + '\'' +
            '}';
    }

    private byte[] convertMetadataSchema(Map<String, Object> metadataSchema) {
        if (metadataSchema != null) {
            Object convertedMetadataSchema = EnvironmentUtils.convertedNestedLists(metadataSchema);
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.writeValueAsBytes(convertedMetadataSchema);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

}
