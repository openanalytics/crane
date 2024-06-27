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
package eu.openanalytics.rdepot.crane.security.auditing;

import eu.openanalytics.rdepot.crane.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
public class AuditingService {

    private final UserService userService;

    private final AuditEventRepository auditEventRepository;

    public AuditingService(UserService userService, AuditEventRepository auditEventRepository) {
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

    @EventListener
    public void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
        String principal = event.getAuthentication().getName();
        auditEventRepository.add(new AuditEvent(principal, "AUTHENTICATION_SUCCESS", createData()));
    }

    @EventListener
    public void onAuthorizationDeniedEvent(AuthorizationDeniedEvent<?> event) {
        String principal = event.getAuthentication().get().getName();
        auditEventRepository.add(new AuditEvent(principal, "AUTHORIZATION_FAILURE", createData()));
    }

    protected void createAuditEvent(String type, Map<String, Object> data) {
        auditEventRepository.add(new AuditEvent(getPrincipal(), type, data));
    }

    private Map<String, Object> createData() {
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
            "request_path", request.getRequestURI(),
            "response_status", 0,
            "remote_address", request.getRemoteAddr()
        );
    }

    private Map<String, Object> createData(HttpServletRequest request, HttpStatus status) {
        return Map.of(
            "request_method", request.getMethod(),
            "request_path", request.getRequestURI(),
            "response_status", status.value(),
            "remote_address", request.getRemoteAddr()
        );
    }

    private String getPrincipal() {
        Authentication authentication = userService.getUser();
        return authentication == null ? "anonymousUser" : authentication.getName();
    }

}
