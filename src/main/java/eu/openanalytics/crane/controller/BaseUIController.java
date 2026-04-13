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
import eu.openanalytics.crane.service.IdentifierService;
import eu.openanalytics.crane.service.LanguageService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.util.ContextPathHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Locale;
import java.util.Map;

public abstract class BaseUIController {

    protected final UserService userService;
    protected final CraneConfig config;
    protected final LanguageService languageService;
    protected final MessageSource messageSource;
    protected final ContextPathHelper contextPathHelper;
    protected final IdentifierService identifierService;

    public BaseUIController(UserService userService,
                            CraneConfig craneConfig,
                            LanguageService languageService,
                            MessageSource messageSource,
                            ContextPathHelper contextPathHelper, IdentifierService identifierService) {
        this.userService = userService;
        this.config = craneConfig;
        this.languageService = languageService;
        this.messageSource = messageSource;
        this.contextPathHelper = contextPathHelper;
        this.identifierService = identifierService;
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
        map.put("contextPath", contextPathHelper.withEndingSlash());
        map.put("resourcePrefix", "/__assets/" + identifierService.instanceId);
        map.put("instanceId", identifierService.instanceId);
        map.put("repository", "");
        if (authenticated) {
            map.put("username", userService.getUser().getName());
        }
        setLanguageConfig(map, httpServletRequest, httpServletResponse);
        map.put("request", httpServletRequest);
        map.put("response", httpServletResponse);
    }

    private void setLanguageConfig(ModelMap map, HttpServletRequest request, HttpServletResponse response) {
        map.put("language", languageService.getAndUpdateLanguageOfUser(request, response));
        map.put("enabledLanguages", languageService.getEnabledLanguages().values());
        map.put("dataTableLanguageUrl", getDataTableLanguageUrl());
    }

    private final static Map<String, String> LOCALE_TO_DATATABLE_LANGUAGE = Map.of(
        "nl", "nl-NL.json",
        "fr", "fr-FR.json",
        "es", "es-ES.json"
    );

    private String getDataTableLanguageUrl() {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale.getLanguage().equals("en")) {
            return "";
        }
        String file = LOCALE_TO_DATATABLE_LANGUAGE.get(locale.getLanguage());
        if (file == null) {
            return ""; // fallback to en
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/__assets/" + identifierService.instanceId + "/webjars/datatables.net-plugins/2.3.6/i18n/" + file).toUriString();
    }

}
