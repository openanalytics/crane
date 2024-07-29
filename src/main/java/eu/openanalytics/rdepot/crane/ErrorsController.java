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
import eu.openanalytics.rdepot.crane.service.UserService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@Controller
public class ErrorsController extends BaseUIController implements ErrorController {

    public ErrorsController(UserService userService, CraneConfig craneConfig) {
        super(userService, craneConfig);
    }

    @RequestMapping(value = "/error", produces = "text/html")
    public ModelAndView handleErrorAsHtml(HttpServletRequest request, HttpServletResponse response, ModelMap map) {
        setNoCacheHeader(response);
        map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        map.put("resource", request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH));

        int status = getStatus(request, response);

        prepareMap(map);
        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new ModelAndView("not-found", HttpStatus.NOT_FOUND);
        }
        return new ModelAndView("not-found", HttpStatus.valueOf(status));
    }

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request, HttpServletResponse response) {
        setNoCacheHeader(response);
        int status = getStatus(request, response);

        String error = "error";
        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value()) {
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
