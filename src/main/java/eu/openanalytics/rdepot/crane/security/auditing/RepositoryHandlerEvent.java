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
package eu.openanalytics.rdepot.crane.security.auditing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RepositoryHandlerEvent extends AuditApplicationEvent {
    public RepositoryHandlerEvent(HttpServletRequest request) {
        super(principal(request), "REPOSITORY_HANDLER", info(request));
    }

    private static String principal(HttpServletRequest request) {
        return Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName).orElse("anonymousUser");
    }

    private static Map<String, Object> info(HttpServletRequest request) {
        Map<String, Object> information = new HashMap<>();
        information.put("method", request.getMethod());
        information.put("path", request.getRequestURI());
        return information;
    }
}
