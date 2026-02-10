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
package eu.openanalytics.crane.model.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class AccessControl {

    private final List<String> network;

    @Getter
    private final boolean anyAuthenticatedUser;

    @Getter
    private final boolean isPublic;

    @Getter
    private final List<String> groups;

    @Getter
    private final List<String> users;

    @Getter
    private final String expression;

    @Getter
    @EqualsAndHashCode.Exclude
    private final List<IpAddressMatcher> networkMatchers;

    @ConstructorBinding
    public AccessControl(List<String> groups, List<String> users, @Name("public") boolean isPublic, List<String> network, Boolean anyAuthenticatedUser, String expression) {
        this.groups = groups;
        this.users = users;
        this.network = network;
        this.isPublic = isPublic;
        if (network != null) {
            this.networkMatchers = network.stream().map(IpAddressMatcher::new).collect(Collectors.toList());
        } else {
            this.networkMatchers = null;
        }
        this.expression = expression;

        if (anyAuthenticatedUser != null && anyAuthenticatedUser && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException("Cannot add user-based access control properties when any-authenticated-user is enabled");
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess() || anyAuthenticatedUser != null)) {
            throw new IllegalArgumentException("Cannot add access control properties to a public repo");
        }

        this.anyAuthenticatedUser = Objects.requireNonNullElse(anyAuthenticatedUser, false);
    }

    public AccessControl() {
        this(null, null, false, null, null, null);
    }

    public boolean hasGroupAccess() {
        return groups != null && !groups.isEmpty();
    }

    public boolean hasUserAccess() {
        return users != null && !users.isEmpty();
    }

    public boolean hasNetworkAccess() {
        return network != null && !network.isEmpty();
    }

    public boolean hasExpressionAccess() {
        return expression != null && !expression.isEmpty();
    }

}
