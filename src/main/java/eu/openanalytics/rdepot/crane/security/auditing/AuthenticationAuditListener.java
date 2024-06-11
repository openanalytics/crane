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

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.security.AbstractAuthenticationAuditListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthenticationAuditListener extends AbstractAuthenticationAuditListener {
    /**
     * Authentication success event type.
     */
    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";
    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        if (event instanceof AuthenticationSuccessEvent authenticationSuccessEvent) {
           onAuthenticationSuccessEvent(authenticationSuccessEvent);
        }
    }
    private void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
        String name = event.getAuthentication().getName();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remoteAddress", getRemoteAddress(event));
        String path = getPath(event);
        data.put("request_path", path);
        publish(new AuditEvent(name, AUTHENTICATION_SUCCESS, data));
    }

    private String getRemoteAddress(AuthenticationSuccessEvent event) {
        try {
            WebAuthenticationDetails details = (WebAuthenticationDetails) event.getAuthentication().getDetails();
            return details.getRemoteAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private String getPath(AuthenticationSuccessEvent event) {
        try {
            SecurityContextHolderAwareRequestWrapper wrapper = (SecurityContextHolderAwareRequestWrapper) event.getSource();
            return wrapper.getRequestURI();
        } catch (Exception e) {
            return "";
        }
    }
}
