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
package eu.openanalytics.rdepot.crane;

import eu.openanalytics.rdepot.crane.model.config.CacheRule;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.security.auditing.AuditingService;
import eu.openanalytics.rdepot.crane.service.UserService;
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

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RepositoryHostingHandler implements HttpRequestHandler {

    private final ResourceHttpMessageConverter resourceHttpMessageConverter = new ResourceHttpMessageConverter();
    private final Path repositoryRoot;
    private final Repository repository;
    private final Map<AntPathRequestMatcher, String> cacheRules;
    private final UserService userService;
    private final AuditingService auditingService;

    public RepositoryHostingHandler(Repository repository, Path repositoryRoot, AuditingService auditingService, UserService userService) {
        this.repository = repository;
        this.repositoryRoot = repositoryRoot;
        this.cacheRules = new HashMap<>();
        this.auditingService = auditingService;
        this.userService = userService;

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
        Path path = getPath(request);

        if (!Files.exists(path)) {
            if (path.endsWith(repository.getIndexFileName())) {
                Path directory = path.getParent();
                if (Files.isDirectory(directory)) {
                    auditingService.createRepositoryHandlerAuditEvent(request);
                    request.setAttribute("path", directory);
                    request.setAttribute("repo", repository);
                    request.getRequestDispatcher("/__index").forward(request, response);
                    return;
                }
            }
            if (request.getUserPrincipal() == null) {
                response.sendRedirect(userService.getLoginPath());
            }
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.NOT_FOUND);
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        if (Files.isDirectory(path)) {
            response.sendRedirect(request.getRequestURI() + "/");
            return;
        }
        if (new ServletWebRequest(request, response).checkNotModified(Files.getLastModifiedTime(path).toMillis())) {
            return;
        }

        auditingService.createRepositoryHandlerAuditEvent(request);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        addCachingHeaders(request, response);

        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        MediaType mediaType = getMediaType(path);
        resourceHttpMessageConverter.write(resource, mediaType, outputMessage);
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
