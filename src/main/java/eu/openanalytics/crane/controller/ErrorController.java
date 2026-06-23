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
import eu.openanalytics.crane.service.IdentifierService;
import eu.openanalytics.crane.service.LanguageService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.util.ContextPathHelper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ErrorController extends BaseUIController implements org.springframework.boot.webmvc.error.ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(ErrorController.class);

    public ErrorController(UserService userService,
                           CraneConfig craneConfig,
                           LanguageService languageService,
                           MessageSource messageSource,
                           ContextPathHelper contextPathHelper,
                           IdentifierService identifierService,
                           SearchConfig searchConfig) {
        super(userService, craneConfig, languageService, messageSource, contextPathHelper, identifierService, searchConfig);
    }

    @RequestMapping(value = "/error", produces = "text/html", method = RequestMethod.GET)
    public ModelAndView handleErrorAsHtml(HttpServletRequest request, HttpServletResponse response, ModelMap map, Authentication auth) {
        int status = getStatus(request, response);

        setNoCacheHeader(response);
        map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        map.put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH));

        prepareMap(map);
        if (status == HttpStatus.BAD_REQUEST.value()) {
            return new ModelAndView("bad-request", HttpStatus.BAD_REQUEST);
        } else if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new ModelAndView("error", HttpStatus.valueOf(status));
        }

        return new ModelAndView("not-found", HttpStatus.NOT_FOUND);
    }

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request, HttpServletResponse response) {
        setNoCacheHeader(response);
        int status = getStatus(request, response);

        String error = "error";
        if (request.getMethod().equals(HttpMethod.POST.name()) && (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value())) {
            // This is an upload request -> always return JSON and forbidden
            error = "forbidden";
            status = 403;
        } else if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value()) {
            error = "not-found";
            status = 404;
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", error);
        resp.put("code", status);
        resp.put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH).toString());
        return new ResponseEntity<>(resp, HttpStatus.valueOf(status));
    }

    @RequestMapping(value = "/logout-success", method = RequestMethod.GET)
    public String getLogoutSuccessPage(HttpServletResponse response, ModelMap map) {
        setNoCacheHeader(response);
        prepareMap(map);
        map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        map.put("authenticated", false);
        return "logout-success";
    }

    public static void accessDeniedHandler(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws ServletException, IOException {
        logger.debug("Request access denied, method: '{}', uri: '{}', exception:", request.getMethod(), request.getRequestURI(), e);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
        request.getRequestDispatcher("/error").forward(request, response);
    }

    /**
     * Add Cache-Control header to never cacher error responses.
     */
    private void setNoCacheHeader(HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noCache().getHeaderValue());
    }

    private int getStatus(HttpServletRequest request, HttpServletResponse response) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null) {
            return Integer.parseInt(status.toString());
        }
        if (response.getStatus() != 200) {
            return response.getStatus();
        }
        return 500;
    }

}
