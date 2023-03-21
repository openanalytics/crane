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
package eu.openanalytics.rdepot.crane;

import io.undertow.util.StatusCodes;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class RedirectIfDirectoryHandler {

    private final ResourcePatternResolver resourcePatternResolver;
    private final String storageHandler;
    private final String storagePath;

    public RedirectIfDirectoryHandler(ResourcePatternResolver resourcePatternResolver, String storageHandler, String storagePath) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.storageHandler = storageHandler;
        this.storagePath = storagePath;
    }

    public boolean handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (path == null) {
            throw new IllegalStateException("Required request attribute '" +
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE + "' is not set");
        }

        if (path.endsWith("/")) {
            // this is already a directory
            return false;
        }

        String fullPath = getFullPath(path);
        if (fullPath == null) {
            response.sendError(StatusCodes.NOT_FOUND);
            return true;
        } else {
            fullPath = storageHandler + fullPath + "/*";
        }

        if (resourcePatternResolver.getResources(fullPath).length > 0) {
            response.sendRedirect(request.getRequestURI() + "/");
            return true;
        } else {
            return false;
        }

    }

    private String getFullPath(String path) {
        if (path.contains("%")) {
            // don't support encoded paths
            return null;
        }

        File file = new File(path);
        if (file.isAbsolute()) {
            // input should not be an absolute path
            return null;
        }

        File fullPath = new File(storagePath + path);

        try {
            String canonicalPath = fullPath.getCanonicalPath();
            if (!fullPath.getAbsolutePath().equals(canonicalPath)) {
                // possible path traversal attack
                return null;
            }
            if (!canonicalPath.startsWith(storagePath)) {
                // possible path traversal attack
                return null;
            }

            return canonicalPath;
        } catch (IOException e) {
            return null;
        }
    }

}
