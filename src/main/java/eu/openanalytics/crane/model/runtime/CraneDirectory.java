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

public class CraneDirectory implements CraneResource {

    private final String name;
    private final String path;
    private final boolean isPathComponent;
    private final boolean hasWriteAccess;

    public CraneDirectory(String name, String path, boolean isPathComponent, boolean hasWriteAccess) {
        this.name = name;
        this.path = path;
        this.isPathComponent = isPathComponent;
        this.hasWriteAccess = hasWriteAccess;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getPath() {
        // path relative to crane root
        return path;
    }

    public boolean isPathComponent() {
        return isPathComponent;
    }

    public boolean hasWriteAccess() {
        return hasWriteAccess;
    }
}
