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
package eu.openanalytics.crane;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.service.LanguageService;
import eu.openanalytics.crane.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class BaseUIController {

    protected final UserService userService;
    protected final CraneConfig config;
    protected final LanguageService languageService;
    protected final MessageSource messageSource;

    public BaseUIController(UserService userService, CraneConfig craneConfig, LanguageService languageService, MessageSource messageSource) {
        this.userService = userService;
        this.config = craneConfig;
        this.languageService = languageService;
        this.messageSource = messageSource;
    }

    protected void prepareMap(ModelMap map) {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        HttpServletResponse httpServletResponse = servletRequestAttributes.getResponse();

        boolean authenticated = userService.isAuthenticated();
        map.put("loginUrl", userService.getLoginUrl());
        map.put("logo", config.getLogoUrl());
        map.put("authenticated", authenticated);
        map.put("isOnlyPublic", config.isOnlyPublic());
        map.put("language", languageService.getAndUpdateLanguageOfUser(httpServletRequest, httpServletResponse));
        if (authenticated) {
            map.put("username", userService.getUser().getName());
        }
        setLanguageConfig(map, httpServletRequest, httpServletResponse);
    }

    private void setLanguageConfig(ModelMap map, HttpServletRequest request, HttpServletResponse response) {
        map.put("language", languageService.getAndUpdateLanguageOfUser(request, response));
        map.put("enabledLanguages", languageService.getEnabledLanguages().values());
    }

}
