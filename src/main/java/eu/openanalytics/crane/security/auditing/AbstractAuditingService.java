/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.security.auditing;

import eu.openanalytics.crane.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

public abstract class AbstractAuditingService {

    private final UserService userService;

    protected final AuditEventRepository auditEventRepository;

    public AbstractAuditingService(UserService userService, AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.auditEventRepository = auditEventRepository;
    }

    public void createErrorHandlerAuditEvent(HttpServletRequest request, HttpStatus status) {
        createAuditEvent("ERROR_HANDLER", createData(request, status));
    }

    public void createLogoutHandlerAuditEvent(HttpServletRequest request) {
        createAuditEvent("LOGOUT", createData(request, HttpStatus.OK));
    }

    public void createRepositoryHandlerAuditEvent(HttpServletRequest request) {
        createAuditEvent("REPOSITORY_HANDLER", createData(request, HttpStatus.OK));
    }

    public void createListRepositoriesAuditEvent(HttpServletRequest request) {
        createAuditEvent("LIST_REPOSITORIES", createData(request, HttpStatus.OK));
    }

    public void createAuthorizationDeniedEvent(Authentication auth) {
        auditEventRepository.add(new AuditEvent(auth.getName(), "AUTHORIZATION_FAILURE", createData()));
    }

    public void createAuditEvent(String type, Map<String, Object> data) {
        auditEventRepository.add(new AuditEvent(getPrincipal(), type, data));
    }

    public Map<String, Object> createData() {
        ServletRequestAttributes attributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        if (attributes == null) {
            return Map.of(
                    "request_method", "",
                    "request_path", "",
                    "response_status", 0,
                    "remote_address", ""
            );
        }
        HttpServletRequest request = attributes.getRequest();
        return Map.of(
                "request_method", request.getMethod(),
                "request_path", preparePath(request.getRequestURI()),
                "response_status", 0,
                "remote_address", request.getRemoteAddr()
        );
    }

    private String preparePath(String requestURI) {
        if (requestURI.contains("__file")) {
            return requestURI.replaceFirst("/__file", "");
        }
        return requestURI;
    }

    public Map<String, Object> createData(HttpServletRequest request, HttpStatus status) {
        return Map.of(
                "request_method", request.getMethod(),
                "request_path", preparePath(request.getRequestURI()),
                "response_status", status.value(),
                "remote_address", request.getRemoteAddr()
        );
    }

    protected String getPrincipal() {
        Authentication authentication = userService.getUser();
        return authentication == null ? "anonymousUser" : authentication.getName();
    }

}
