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

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import eu.openanalytics.crane.model.dto.CraneDirectoryDto;
import eu.openanalytics.crane.model.dto.CraneFileDto;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.service.IdentifierService;
import eu.openanalytics.crane.service.IndexPageService;
import eu.openanalytics.crane.service.LanguageService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import eu.openanalytics.crane.util.CleanHtml;
import eu.openanalytics.crane.util.ContextPathHelper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IndexPageController extends BaseUIController {

    private final IndexPageService indexPageService;
    private final UploadAccessControlService uploadAccessControlService;
    private final SpecExpressionResolver specExpressionResolver;
    private final String robotsTxt;

    public IndexPageController(IndexPageService indexPageService,
                               UserService userService,
                               CraneConfig craneConfig,
                               LanguageService languageService,
                               MessageSource messageSource,
                               ContextPathHelper contextPathHelper,
                               UploadAccessControlService uploadAccessControlService,
                               IdentifierService identifierService,
                               SearchConfig searchConfig,
                               SpecExpressionResolver specExpressionResolver) {
        super(userService, craneConfig, languageService, messageSource, contextPathHelper, identifierService, searchConfig);
        this.indexPageService = indexPageService;
        this.uploadAccessControlService = uploadAccessControlService;
        this.specExpressionResolver = specExpressionResolver;
        robotsTxt = craneConfig.getRobotsTxt();
    }

    @GetMapping(value = "/__index", produces = MediaType.TEXT_HTML_VALUE)
    public String main(ModelMap map, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        CranePath path = (CranePath) request.getAttribute("path");
        Repository repo = (Repository) request.getAttribute("repo");

        if (path == null || repo == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return null;
        }

        String resource = (String) request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        resource = resource.replace(repo.getIndexFileName(), "");

        prepareMap(map);

        map.put("resource", resource);

        Pair<List<CranePath>, List<CranePath>> filesAndDirectoriesInPath = indexPageService.getFilesAndDirectoriesInPath(path);

        map.put("files", filesAndDirectoriesInPath.getFirst());
        map.put("directories", filesAndDirectoriesInPath.getSecond());
        map.put("hasWriteAccess", uploadAccessControlService.canAccess(path));
        map.put("repository", repo.getName());
        map.put("path", path.getWebUri());
        if (path.isDotCraneDirectory() || path.isMetadataDirectory() || path.isExtractedMetadataDirectory()) {
            map.put("hasMetadataWriteAccess", false);
            map.put("hasCreateDirectoryAccess", false);
        } else {
            map.put("hasMetadataWriteAccess", path.getRepository().getMetadataSchema() != null && uploadAccessControlService.canAccess(path.getPath(".crane").getPath(path.getRepository().getMetadataDirectoryName())));
            map.put("hasCreateDirectoryAccess", uploadAccessControlService.canAccess(path));
        }
        map.put("breadcrumbs", path.getComponents());
        map.put("uploadInstructions", getUploadInstructions(path));

        return indexPageService.getTemplateName(repo);
    }

    @ResponseBody
    @GetMapping(value = "/__index", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> mainJson(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        CranePath path = (CranePath) request.getAttribute("path");
        Repository repo = (Repository) request.getAttribute("repo");

        if (path == null || repo == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return null;
        }

        Map<String, Object> data = new HashMap<>();

        Pair<List<CranePath>, List<CranePath>> filesAndDirectoriesInPath = indexPageService.getFilesAndDirectoriesInPath(path);

        List<CraneFileDto> files = filesAndDirectoriesInPath.getFirst().stream().map(CraneFileDto::new).toList();
        List<CraneDirectoryDto> directories = filesAndDirectoriesInPath.getSecond().stream().map(CraneDirectoryDto::new).toList();

        data.put("files", files);
        data.put("directories", directories);

        return ApiResponse.success(data);
    }

    private String getUploadInstructions(CranePath path) {
        if (config.getUploadInstructions() != null && !config.getUploadInstructions().isBlank()) {
            SpecExpressionContext context = SpecExpressionContext.create(path);
            return CleanHtml.cleanRelaxed(specExpressionResolver.evaluateToString(config.getUploadInstructions(), context));
        }
        return null;
    }

    @ResponseBody
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robotsTxt() {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(robotsTxt);
    }

}
