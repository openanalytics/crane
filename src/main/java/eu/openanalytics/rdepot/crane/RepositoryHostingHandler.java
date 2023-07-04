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

import eu.openanalytics.rdepot.crane.model.CacheRule;
import eu.openanalytics.rdepot.crane.model.Repository;
import org.apache.tika.Tika;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RepositoryHostingHandler implements HttpRequestHandler {

    private final ResourceHttpMessageConverter resourceHttpMessageConverter = new ResourceHttpMessageConverter();
    private final Path repositoryRoot;
    private final Repository repository;
    private final Map<AntPathRequestMatcher, String> cacheRules;

    public RepositoryHostingHandler(Repository repository, Path repositoryRoot) {
        this.repository = repository;
        this.repositoryRoot = repositoryRoot;
        this.cacheRules = new HashMap<>();

        if (repository.getCache() != null) {
            for (CacheRule cache : repository.getCache()) {
                cacheRules.put(
                    new AntPathRequestMatcher(cache.getPattern()),
                    CacheControl.maxAge(cache.getMaxAge()).getHeaderValue()
                );
            }
        }
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Optional<Path> path = getPath(request);
        if (path.isEmpty()) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }

        if (!Files.exists(path.get())) {
            if (path.get().endsWith(repository.getIndexFileName())) {
                Path directory = path.get().getParent();
                if (Files.isDirectory(directory)) {
                    request.setAttribute("path", directory);
                    request.setAttribute("repo", repository);
                    request.getRequestDispatcher("/__index").forward(request, response);
                    return;
                }
            }
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        if (Files.isDirectory(path.get())) {
            response.sendRedirect(request.getRequestURI() + "/");
            return;
        }
        if (new ServletWebRequest(request, response).checkNotModified(Files.getLastModifiedTime(path.get()).toMillis())) {
            return;
        }

        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path.get()));
        addCachingHeaders(request, response);

        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        MediaType mediaType = getMediaType(path.get());
        resourceHttpMessageConverter.write(resource, mediaType, outputMessage);
    }

    private Optional<Path> getPath(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (path.contains("%")) {
            // don't support encoded paths
            return Optional.empty();
        }

        Path file = Path.of(path);
        if (file.isAbsolute()) {
            // path should be a relative path
            return Optional.empty();
        }

        // TODO test this
        File absolutePath = new File( "/" + path);

        try {
            String canonicalPath = absolutePath.getCanonicalPath();
            if (!new File(canonicalPath).isAbsolute()) {
                return Optional.empty();
            }
            if (!absolutePath.getAbsolutePath().equals(canonicalPath)) {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }

        // TODO is it save to use path?
        if (request.getRequestURI().endsWith("/")) {
            return Optional.of(repositoryRoot.resolve(path).resolve(repository.getIndexFileName()));
        } else {
            return Optional.of(repositoryRoot.resolve(path));
        }

    }

    private void addCachingHeaders(HttpServletRequest request, HttpServletResponse response) {
        for (Map.Entry<AntPathRequestMatcher, String> cacheRule : cacheRules.entrySet()) {
            if (cacheRule.getKey().matches(request)) {
                response.setHeader("Cache-Control", cacheRule.getValue());
                break;
            }
        }
    }

    private MediaType getMediaType(Path path) {
        Tika tika = new Tika();
        String mimeType = tika.detect(path.getFileName().toString());
        return MediaType.valueOf(mimeType);
    }

}
