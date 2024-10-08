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
package eu.openanalytics.crane;

import eu.openanalytics.crane.model.config.Repository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.file.Path;

public class RepositoryHostingHandler implements HttpRequestHandler {

    private final Path repositoryRoot;
    private final Repository repository;

    public RepositoryHostingHandler(Repository repository, Path repositoryRoot) {
        this.repository = repository;
        this.repositoryRoot = repositoryRoot;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Path path = getPath(request);
        String newURI = path.toString().substring(repository.getStorageLocation().length());
        request.setAttribute("path", path);
        request.setAttribute("repo", repository);
        request.getRequestDispatcher("/__file/" + newURI).forward(request, response);
    }

    private Path getPath(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (Path.of(path).isAbsolute()) {
            throw new IllegalStateException("Path should be relative");
        }
        if (request.getRequestURI().endsWith("/")) {
            return repositoryRoot.resolve(path).resolve(repository.getIndexFileName());
        } else {
            return repositoryRoot.resolve(path);
        }
    }

}
