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
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.service.AccessControlService;
import eu.openanalytics.rdepot.crane.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class MainController {

    private final CraneConfig config;

    private final AccessControlService accessControlService;

    private final UserService userService;

    public MainController(CraneConfig config, AccessControlService accessControlService, UserService userService) {
        this.config = config;
        this.accessControlService = accessControlService;
        this.userService = userService;
    }

    @GetMapping("/.well-known/configured-openid-configuration")
    public void getOpenIdConfigurationUrl(HttpServletResponse response) {
        response.setHeader("Location", config.getConfiguredOpenIdMetadataUrl());
        response.setStatus(302);
    }

    @GetMapping(value = "/", produces = "text/html")
    public String getRepositoriesAsHtml(ModelMap map) {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = userService.isAuthenticated();

        List<String> repositories = config.getRepositories().stream()
            .filter(r -> accessControlService.canAccessRepository(user, r))
            .map(Repository::getName)
            .collect(Collectors.toList());

        if (repositories.isEmpty() && !authenticated) {
            // no repositories found and not authenticated -> ask the user to login
            return "redirect:" + userService.getLoginUrl();
        }

        map.put("repositories", repositories);
        map.put("authenticated", authenticated);
        map.put("loginUrl", userService.getLoginUrl());

        return "repositories";
    }

}
