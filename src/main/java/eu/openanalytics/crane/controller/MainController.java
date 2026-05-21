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
import eu.openanalytics.crane.model.dto.LanguageDto;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.IdentifierService;
import eu.openanalytics.crane.service.LanguageService;
import eu.openanalytics.crane.service.PathReadAccessControlService;
import eu.openanalytics.crane.service.PosixReadAccessControlService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.util.ContextPathHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class MainController extends BaseUIController {
    private final PathReadAccessControlService pathReadAccessControlService;
    private final PosixReadAccessControlService posixReadAccessControlService;
    private final AuditingService auditingService;
    private final UserService userService;

    public MainController(CraneConfig config,
                          UserService userService,
                          PathReadAccessControlService pathReadAccessControlService,
                          PosixReadAccessControlService posixReadAccessControlService,
                          AuditingService auditingService,
                          LanguageService languageService,
                          MessageSource messageSource,
                          ContextPathHelper contextPathHelper,
                          IdentifierService identifierService,
                          SearchConfig searchConfig) {
        super(userService, config, languageService, messageSource, contextPathHelper, identifierService, searchConfig);
        this.pathReadAccessControlService = pathReadAccessControlService;
        this.posixReadAccessControlService = posixReadAccessControlService;
        this.auditingService = auditingService;
        this.userService = userService;
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
            .filter(r -> pathReadAccessControlService.canAccess(new CranePath(r, "")) && posixReadAccessControlService.canAccess(new CranePath(r, "")))
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
                    .filter(r -> pathReadAccessControlService.canAccess(new CranePath(r, "")) && posixReadAccessControlService.canAccess(new CranePath(r, "")))
                    .map(Repository::getName)
                    .toList()
            )
        );
    }

    @ResponseBody
    @GetMapping(value = "/__user/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserMetadata() {
        if (!userService.isAuthenticated()) {
            return ApiResponse.success(
                Map.of("authenticated", false)
            );
        }
        Authentication user = userService.getUser();
        return ApiResponse.success(
            Map.of(
                "authenticated", true,
                "username", user.getName(),
                "groups", UserService.getGroups(user)
            )
        );
    }

    @ResponseBody
    @PutMapping(value = "/__language", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> updateUserLanguage(@RequestBody LanguageDto body, HttpServletRequest request, HttpServletResponse response) {
        try {
            languageService.updateLanguageOfUser(request, response, body.language());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(e);
        }

        return ApiResponse.success();
    }

    private final static List<String> INTERNATIONALIZATION_MESSAGES_CODES = List.of(
        "index.create.button",
        "index.creating.button",
        "index.not_allowed_created_directory.error",
        "index.retry.button",
        "index.uploading",
        "index.delete_file.confirmation",
        "index.delete_directory.confirmation",
        "index.empty_directory.confirmation"
    );

    @ResponseBody
    @GetMapping(value = "/__language/messages/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> getLanguageMessages(@PathVariable String language) {
        if (!languageService.getEnabledLanguages().containsKey(language)) {
            return ApiResponse.fail("Language not supported.");
        }
        Locale locale = Locale.forLanguageTag(language);

        Map<String, String> internationalizationMessages = new HashMap<>();
        for (String code : INTERNATIONALIZATION_MESSAGES_CODES) {
            internationalizationMessages.put(code, messageSource.getMessage(code, null, locale));
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .body(new ApiResponse<>("success", internationalizationMessages));
    }

    @ResponseBody
    @GetMapping("/__csrf")
    public ResponseEntity<ApiResponse<CsrfToken>> csrf(CsrfToken csrfToken) {
        // see https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-mobile
        return ApiResponse.success( csrfToken);
    }

}
