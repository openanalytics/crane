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

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.model.runtime.CraneDirectory;
import eu.openanalytics.rdepot.crane.model.runtime.CraneFile;
import eu.openanalytics.rdepot.crane.service.IndexPageService;
import eu.openanalytics.rdepot.crane.service.UserService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IndexPageController extends BaseUIController {

    private final IndexPageService indexPageService;

    public IndexPageController(IndexPageService indexPageService, UserService userService, CraneConfig craneConfig) {
        super(userService, craneConfig);
        this.indexPageService = indexPageService;
    }

    @GetMapping(value = "/__index", produces = MediaType.TEXT_HTML_VALUE)
    public String main(ModelMap map, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Path path = (Path) request.getAttribute("path");
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
        map.putAll(indexPageService.getTemplateVariables(repo, path));
        return indexPageService.getTemplateName(repo);
    }

    @ResponseBody
    @GetMapping(value = "/__index", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<String>> mainJson(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Path path = (Path) request.getAttribute("path");
        Repository repo = (Repository) request.getAttribute("repo");

        if (path == null || repo == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return null;
        }

        Map<String, Object> variables = indexPageService.getTemplateVariables(repo, path);
        Map<String, List<String>> filesAndDirectories = new HashMap<>();
        filesAndDirectories.put("directories", ((List<CraneDirectory>) variables.get("directories")).stream().map(CraneDirectory::getName).toList());
        filesAndDirectories.put("files", ((List<CraneFile>) variables.get("files")).stream().map(CraneFile::getName).toList());
        return filesAndDirectories;
    }
}
