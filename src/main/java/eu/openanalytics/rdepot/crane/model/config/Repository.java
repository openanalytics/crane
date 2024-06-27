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
package eu.openanalytics.rdepot.crane.model.config;

import java.nio.file.Path;
import java.util.List;

public class Repository extends PathComponent {

    private String indexFileName = "index.html";
    private List<CacheRule> cache;
    private String storageLocation;
    private Path storagePath;

    public String getIndexFileName() {
        return indexFileName;
    }

    public void setIndexFileName(String indexFileName) {
        this.indexFileName = indexFileName;
    }

    public List<CacheRule> getCache() {
        return cache;
    }

    public void setCache(List<CacheRule> cache) {
        this.cache = cache;
    }

    public void validate() {
        super.validate();

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add access control properties to a public repo", getName()));
        }
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(Path storagePath) {
        this.storagePath = storagePath;
    }
}
