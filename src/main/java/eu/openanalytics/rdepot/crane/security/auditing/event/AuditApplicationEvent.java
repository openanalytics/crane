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
package eu.openanalytics.rdepot.crane.security.auditing.event;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AuditApplicationEvent extends org.springframework.boot.actuate.audit.listener.AuditApplicationEvent {
    public AuditApplicationEvent(String principal, String type, Map<String, Object> data) {
        super(principal, type, data);
    }

    static String principal(HttpServletRequest request, String defaultName) {
        return Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName).orElse(defaultName);
    }

    static String principal(HttpServletRequest request) {
        return principal(request, "anonymousUser");
    }

    static Map<String, Object> info(HttpServletRequest request) {
        Map<String, Object> information = new HashMap<>();
        information.put("request_method", request.getMethod());
        information.put("request_path", request.getRequestURI());
        return information;
    }

    static Map<String, Object> info(HttpServletRequest request, int response_status) {
        Map<String, Object> information = info(request);
        information.put("response_status", response_status);
        return information;
    }
}
