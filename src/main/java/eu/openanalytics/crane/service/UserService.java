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
package eu.openanalytics.crane.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class UserService {

    private final String clientRegistrationId;

    public UserService(@Autowired(required = false) InMemoryClientRegistrationRepository clientRegistrationRepository) {
        if (clientRegistrationRepository != null) {
            clientRegistrationId = clientRegistrationRepository.iterator().next().getRegistrationId();
        } else {
            clientRegistrationId = null;
        }
    }

    public String getLoginPath() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI)
                .path("/")
                .path(clientRegistrationId)
                .build().getPath();
    }

    public String getLoginUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI)
                .path("/")
                .path(clientRegistrationId)
                .build().toString();
    }

    public boolean isAuthenticated() {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        return !(user instanceof AnonymousAuthenticationToken);
    }

    public Authentication getUser() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuth : auth.getAuthorities()) {
            String groupName = grantedAuth.getAuthority().toUpperCase();
            if (groupName.startsWith("ROLE_")) {
                groupName = groupName.substring(5);
            }
            if (groupName.equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false;
    }
}
