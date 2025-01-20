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

import com.google.common.collect.Streams;
import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.CacheRule;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.HandleSpecExpressionService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tika.Tika;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Controller
public class DownloadController {
    private final ResourceHttpMessageConverter resourceHttpMessageConverter = new ResourceHttpMessageConverter();
    private final AuditingService auditingService;
    private final HandleSpecExpressionService handleSpecExpressionService;
    private final CraneConfig craneConfig;

    public DownloadController(AuditingService auditingService, HandleSpecExpressionService handleSpecExpressionService, CraneConfig craneConfig) {
        this.auditingService = auditingService;
        this.handleSpecExpressionService = handleSpecExpressionService;
        this.craneConfig = craneConfig;
    }

    @PreAuthorize("@readAccessControlService.canAccess(#r, #p)")
    @GetMapping("/__file/{repository}/{*path}")
    public void read(HttpServletRequest request,
                     HttpServletResponse response,
                     @P("r") @PathVariable(name = "repository") String stringRepository,
                     @P("p") @PathVariable(name = "path") String stringPath) throws ServletException, IOException {
        Repository repository = craneConfig.getRepository(stringRepository);
        String relativePath = String.join("/", Streams.stream(Path.of(stringPath).iterator()).map(Path::toString).toList());
        Path path = repository.getStoragePath().resolve(relativePath);
        if (!stringPath.endsWith("/") && Files.isDirectory(path)) {
            response.sendRedirect(request.getRequestURI().replaceFirst("/__file", "") + "/");
            return;
        }
        if (Files.isDirectory(path)) {
            path = path.resolve(repository.getIndexFileName());
        }
        if (!Files.exists(path)) {
            if (path.endsWith(repository.getIndexFileName())) {
                Path directory = path.getParent();
                if (Files.isDirectory(directory)) {
                    request.setAttribute("path", directory);
                    request.setAttribute("repo", repository);
                    auditingService.createRepositoryHandlerAuditEvent(request);
                    request.getRequestDispatcher("/__index").forward(request, response);
                    return;
                }
            }

            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.NOT_FOUND);
            if (handleSpecExpressionService.handleByOnErrorExpression(repository, request, response, HttpStatus.NOT_FOUND.value())) {
                return;
            }
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }

        if (new ServletWebRequest(request, response).checkNotModified(Files.getLastModifiedTime(path).toMillis())) {
            return;
        }

        auditingService.createRepositoryHandlerAuditEvent(request);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));

        addCachingHeaders(request, response, repository);

        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        MediaType mediaType = getMediaType(path);
        resourceHttpMessageConverter.write(resource, mediaType, outputMessage);
    }

    private void addCachingHeaders(HttpServletRequest request, HttpServletResponse response, Repository repository) {
        for (Map.Entry<AntPathRequestMatcher, String> cacheRule : computeCacheRules(repository).entrySet()) {
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

    private Map<AntPathRequestMatcher, String> computeCacheRules(Repository repository) {
        Map<AntPathRequestMatcher, String> cacheRules = new HashMap<>();
        if (repository.getCache() != null) {
            for (CacheRule cache : repository.getCache()) {
                cacheRules.put(
                        new AntPathRequestMatcher(cache.getPattern()),
                        CacheControl.maxAge(cache.getMaxAge()).getHeaderValue()
                );
            }
        }
        return cacheRules;
    }

}
