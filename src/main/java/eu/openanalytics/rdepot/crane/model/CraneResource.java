/**
 * Crane
 *
 * Copyright (C) 2021-2022 Open Analytics
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
package eu.openanalytics.rdepot.crane.model;

import eu.openanalytics.rdepot.crane.config.CraneConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public interface CraneResource {

    public String getName();

    public static CraneResource createFromPath(Path path, CraneConfig config) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);

            if (attributes.isDirectory()) {
                return new CraneDirectory(path.getFileName().toString(), "/" + config.getRoot().relativize(path));
            } else {
                return new CraneFile(path.getFileName().toString(), attributes.lastModifiedTime().toInstant(), attributes.size());
            }
        } catch (IOException e) {
            return null;
        }
    }

}
