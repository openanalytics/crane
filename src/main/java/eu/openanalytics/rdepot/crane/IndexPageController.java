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

import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.service.IndexPageService;
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

    public IndexPageController(IndexPageService indexPageService) {
        this.indexPageService = indexPageService;
    }

    @GetMapping("/__index")
    public String main(ModelMap map, HttpServletRequest request) throws IOException {
        Path path = (Path) request.getAttribute("path");
        Repository repo = (Repository) request.getAttribute("repo");

        String resource = (String) request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        resource = resource.replace(repo.getIndexFileName(), "");
        map.put("resource", resource);
        map.putAll(indexPageService.getTemplateVariables(repo, path));

        return indexPageService.getTemplateName(repo);
    }

}
