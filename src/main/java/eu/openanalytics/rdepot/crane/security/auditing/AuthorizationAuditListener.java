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
import org.springframework.boot.actuate.security.AbstractAuthorizationAuditListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authorization.event.AuthorizationEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class AuthorizationAuditListener extends AbstractAuthorizationAuditListener {

    /**
     * Authorization failure event type.
     */
    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";
    private final Set<String> ignorePaths = new HashSet<>(List.of("/favicon.ico"));

    @Override
    public void onApplicationEvent(AuthorizationEvent event) {
        if (event instanceof AuthorizationDeniedEvent<?> authorizationDeniedEvent) {
            onAuthorizationDeniedEvent(authorizationDeniedEvent);
        }
    }

    private void onAuthorizationDeniedEvent(AuthorizationDeniedEvent<?> event) {
        String name = getName(event.getAuthentication());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remoteAddress", getRemoteAddress(event));
        if (name.equals("anonymousUser")) {
            return;
        }
        publish(new AuditEvent(name, AUTHORIZATION_FAILURE, data));
    }

    private String getRemoteAddress(AuthorizationDeniedEvent<?> event) {
        try {
            SecurityContextHolderAwareRequestWrapper wrapper = (SecurityContextHolderAwareRequestWrapper) event.getSource();
            return wrapper.getRemoteAddr();
        } catch (Exception e) {
            return "";
        }
    }

    private String getName(Supplier<Authentication> authentication) {
        try {
            return authentication.get().getName();
        }
        catch (Exception ex) {
            return "anonymousUser";
        }
    }

    private Object getDetails(Supplier<Authentication> authentication) {
        try {
            return authentication.get().getDetails();
        }
        catch (Exception ex) {
            return null;
        }
    }

}
