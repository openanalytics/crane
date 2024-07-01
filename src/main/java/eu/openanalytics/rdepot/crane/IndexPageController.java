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
import eu.openanalytics.rdepot.crane.service.IndexPageService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;

@Controller
public class IndexPageController {

    private final IndexPageService indexPageService;

    private final CraneConfig config;

    public IndexPageController(IndexPageService indexPageService, CraneConfig config) {
        this.indexPageService = indexPageService;
        this.config = config;
    }

    @GetMapping("/__index")
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
        config.prepareMap(map);
        map.put("resource", resource);
        map.putAll(indexPageService.getTemplateVariables(repo, path));
        return indexPageService.getTemplateName(repo);
    }

}
