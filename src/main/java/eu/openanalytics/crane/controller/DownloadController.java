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
package eu.openanalytics.crane.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.indexer.ITextExtractor;
import eu.openanalytics.crane.model.config.CacheRule;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.config.RewriteRule;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.HandleSpecExpressionService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.P;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class DownloadController {

    private final ResourceHttpMessageConverter resourceHttpMessageConverter = new ResourceHttpMessageConverter();
    private final AuditingService auditingService;
    private final HandleSpecExpressionService handleSpecExpressionService;
    private final CraneConfig craneConfig;
    private final SpecExpressionResolver specExpressionResolver;
    private final UserService userService;
    private final ITextExtractor textExtractor;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DownloadController(AuditingService auditingService,
                              HandleSpecExpressionService handleSpecExpressionService,
                              CraneConfig craneConfig,
                              SpecExpressionResolver specExpressionResolver,
                              UserService userService,
                              Optional<ITextExtractor> textExtractor) {
        this.auditingService = auditingService;
        this.handleSpecExpressionService = handleSpecExpressionService;
        this.craneConfig = craneConfig;
        this.specExpressionResolver = specExpressionResolver;
        this.userService = userService;
        this.textExtractor = textExtractor.orElse(null);
    }

    @PreAuthorize("@readAccessControlService.canAccess(#r, #p)")
    @GetMapping("/__file/{repository}/{*path}")
    public void read(HttpServletRequest request,
                     HttpServletResponse response,
                     @P("r") @PathVariable(name = "repository") String stringRepository,
                     @P("p") @PathVariable(name = "path") String stringPath) throws ServletException, IOException {
        Repository repository = craneConfig.getRepository(stringRepository);
        CranePath cranePath = repository.getPath(stringPath);

        if (!stringPath.endsWith("/") && cranePath.isDirectory()) {
            response.sendRedirect(request.getRequestURI().replaceFirst("/__file", "") + "/");
            return;
        }

        Optional<String> redirect = checkRewriteRules(repository, cranePath, request, response);
        if (redirect.isPresent()) {
            logger.debug("Rewriting '{}' to '{}'", stringPath, redirect.get());
            request.getRequestDispatcher("/__file" + redirect.get()).forward(request, response);
            return;
        }

        if (cranePath.isMetadataSchemaFile()) {
            handleMetadataSchemaFile(repository, request, response);
            return;
        }

        if (cranePath.isExtractedMetadataFile()) {
            handleExtractedMetadataFile(cranePath, request, response);
            return;
        }

        if (cranePath.isDirectory()) {
            cranePath = cranePath.getIndexFilePath();
        }

        if (!cranePath.isExists()) {
            if (cranePath.isIndexFile()) {
                CranePath directory = cranePath.getParent();
                if (directory != null && directory.isDirectory()) {
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

        if (new ServletWebRequest(request, response).checkNotModified(cranePath.getLastModifiedTimeInMillis())) {
            return;
        }

        auditingService.createRepositoryHandlerAuditEvent(request);
        InputStreamResource resource = new InputStreamResource(cranePath.asInputStream());

        addCachingHeaders(request, response, repository);

        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        resourceHttpMessageConverter.write(resource, cranePath.getMediaType(), outputMessage);
    }

    private void addCachingHeaders(HttpServletRequest request, HttpServletResponse response, Repository repository) {
        for (Map.Entry<AntPathRequestMatcher, String> cacheRule : computeCacheRules(repository).entrySet()) {
            if (cacheRule.getKey().matches(request)) {
                response.setHeader("Cache-Control", cacheRule.getValue());
                break;
            }
        }
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

    private Optional<String> checkRewriteRules(Repository repository, CranePath cranePath, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = userService.getUser();
        if (repository.getRewrites() == null) {
            return Optional.empty();
        }
        for (RewriteRule redirectRule : repository.getRewrites()) {
            SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), repository, request, response, cranePath);
            if (specExpressionResolver.evaluateToBoolean(redirectRule.getMatcher(), context)) {
                return Optional.of(specExpressionResolver.evaluateToString(redirectRule.getDestination(), context));
            }
        }
        return Optional.empty();
    }

    private void handleMetadataSchemaFile(Repository repository, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        byte[] metadataSchema = repository.getMetadataSchema();
        if (metadataSchema == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.NOT_FOUND);
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        auditingService.createRepositoryHandlerAuditEvent(request);
        ByteArrayResource resource = new ByteArrayResource(metadataSchema);
        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        resourceHttpMessageConverter.write(resource, MediaType.APPLICATION_JSON, outputMessage);
    }

    private void handleExtractedMetadataFile(CranePath cranePath, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        if (textExtractor == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        CranePath targetFile = cranePath.getParent().getParent().getParent().getPath(Strings.CS.removeEnd(cranePath.getName(), ".yaml"));
        byte[] result = objectMapper.writeValueAsBytes(textExtractor.extractMetadata(targetFile));

        ByteArrayResource resource = new ByteArrayResource(result);
        resourceHttpMessageConverter.write(resource, MediaType.APPLICATION_JSON, outputMessage);
    }

}
