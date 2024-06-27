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
package eu.openanalytics.rdepot.crane.model.runtime;

import eu.openanalytics.rdepot.crane.util.FileSize;

import java.time.Instant;

public class CraneFile implements CraneResource {

    private final String name;

    private final Instant lastModifiedTime;

    private final Long size;

    private final String formattedSize;

    public CraneFile(String name, Instant lastModifiedTime, Long size) {
        this.name = name;
        this.lastModifiedTime = lastModifiedTime;
        this.size = size;
        this.formattedSize = FileSize.bytesToHumanReadableBinary(size);
    }

    @Override
    public String getName() {
        return name;
    }

    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }

    public Long getSize() {
        return size;
    }

    public String getFormattedSize() {
        return formattedSize;
    }

}
