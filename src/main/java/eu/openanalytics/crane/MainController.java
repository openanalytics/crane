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

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.PathReadAccessControlService;
import eu.openanalytics.crane.service.PosixReadAccessControlService;
import eu.openanalytics.crane.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class MainController extends BaseUIController {
    private final PathReadAccessControlService pathReadAccessControlService;
    private final PosixReadAccessControlService posixReadAccessControlService;
    private final AuditingService auditingService;

    public MainController(CraneConfig config, UserService userService, PathReadAccessControlService pathReadAccessControlService, PosixReadAccessControlService posixReadAccessControlService, AuditingService auditingService) {
        super(userService, config);
        this.pathReadAccessControlService = pathReadAccessControlService;
        this.posixReadAccessControlService = posixReadAccessControlService;
        this.auditingService = auditingService;
    }

    @GetMapping("/.well-known/configured-openid-configuration")
    public void getOpenIdConfigurationUrl(HttpServletResponse response) {
        response.setHeader("Location", config.getConfiguredOpenIdMetadataUrl());
        response.setStatus(302);
    }

    @GetMapping(value = "/", produces = "text/html")
    public String getRepositoriesAsHtml(HttpServletRequest request, ModelMap map) {
        boolean authenticated = userService.isAuthenticated();

        List<String> repositories = config.getRepositories().stream()
                .filter(r -> pathReadAccessControlService.canAccess(r) && posixReadAccessControlService.canAccess(r))
                .map(Repository::getName)
                .collect(Collectors.toList());

        auditingService.createListRepositoriesAuditEvent(request);

        if (repositories.isEmpty() && !authenticated) {
            // no repositories found and not authenticated -> ask the user to login
            return "redirect:" + userService.getLoginUrl();
        }

        map.put("repositories", repositories);
        prepareMap(map);
        return "repositories";
    }

    @ResponseBody
    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRepositoriesAsJson(HttpServletRequest request) {
        return ApiResponse.success(
                Map.of(
                        "directories",
                        config.getRepositories().stream()
                                .filter(r -> pathReadAccessControlService.canAccess(r) && posixReadAccessControlService.canAccess(r))
                                .map(Repository::getName)
                                .toList()
                )
        );
    }
}
