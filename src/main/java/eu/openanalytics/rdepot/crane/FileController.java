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

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.CacheRule;
import eu.openanalytics.rdepot.crane.model.Repository;
import eu.openanalytics.rdepot.crane.service.IndexPageService;
import org.apache.tika.Tika;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Controller
public class FileController {

    private final CraneConfig config;
    private final Map<String, Map<AntPathRequestMatcher, String>> cacheRules;
    private final ResourceHttpMessageConverter resourceHttpMessageConverter = new ResourceHttpMessageConverter();
    private final Path root;

    public FileController(CraneConfig config, IndexPageService indexPageService) {
        this.config = config;
        root = config.getRoot();
        cacheRules = new HashMap<>();

        for (Repository repository : config.getRepositories()) {
            Map<AntPathRequestMatcher, String> repoCacheRules = new HashMap<>();
            if (repository.getCache() != null) {
                for (CacheRule cache : repository.getCache()) {
                    repoCacheRules.put(
                        new AntPathRequestMatcher(cache.getPattern()),
                        CacheControl.maxAge(cache.getMaxAge()).getHeaderValue()
                    );
                }
            }
            cacheRules.put(repository.getName(), repoCacheRules);
        }
    }

    @GetMapping("/{repoName}/**")
    public void downloadFile(@PathVariable("repoName") String repoName, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Repository repo = config.getRepository(repoName);
        if (repo == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }

        Optional<Path> path = getPath(request);
        if (path.isEmpty()) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }

        if (request.getRequestURI().endsWith("/") || request.getRequestURI().endsWith("/" + repo.getIndexFileName())) {
            if (request.getRequestURI().endsWith("/" + repo.getIndexFileName())) {
                request.setAttribute("path", path.get().getParent());
            } else {
                request.setAttribute("path", path.get());
            }
            request.setAttribute("repo", repo);
            request.getRequestDispatcher("/__index").forward(request, response);
            return;
        }

        if (!Files.exists(path.get())) {
            // maybe a directory?
            try {
                try (Stream<Path> dirListing = Files.list(path.get())) {
                    if (dirListing.findFirst().isPresent()) {
                        response.sendRedirect(request.getRequestURI() + "/");
                        return;
                    }
                }
            } catch (NoSuchFileException ex) {
                // no-op
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
        addCachingHeaders(request, response, repo);

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

        File file = new File(path);
        if (!file.isAbsolute()) {
            // path should be an absolute path
            return Optional.empty();
        }

        try {
            String canonicalPath = file.getCanonicalPath();
            if (!new File(canonicalPath).isAbsolute()) {
                // canonicalPath should be an absolute path
                return Optional.empty();
            }
            if (!file.getAbsolutePath().equals(canonicalPath)) {
                // possible path traversal attack
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }

        return Optional.of(root.resolve(path.replaceFirst("/", "")));
    }

    private void addCachingHeaders(HttpServletRequest request, HttpServletResponse response, Repository repo) {
        for (Map.Entry<AntPathRequestMatcher, String> cacheRule : cacheRules.get(repo.getName()).entrySet()) {
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
