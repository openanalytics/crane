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
package eu.openanalytics.crane.model.config;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.stream.Collectors;

public class AccessControl {

    protected List<String> groups;
    protected List<String> users;
    protected boolean isPublic = false;
    protected List<String> network;
    protected Boolean anyAuthenticatedUser;
    protected List<IpAddressMatcher> networkMatchers;
    protected String expression;

    public AccessControl() {}

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    public boolean hasGroupAccess() {
        return groups != null && groups.size() > 0;
    }

    public boolean hasUserAccess() {
        return users != null && users.size() > 0;
    }

    public boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public List<String> getNetwork() {
        return network;
    }

    public void setNetwork(List<String> accessIpRanges) {
        this.network = accessIpRanges;
        this.networkMatchers = accessIpRanges.stream()
                .map(IpAddressMatcher::new).collect(Collectors.toList());
    }

    public List<IpAddressMatcher> getNetworkMatchers() {
        return networkMatchers;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public boolean hasNetworkAccess() {
        return network != null && !network.isEmpty();
    }

    public boolean hasExpressionAccess() {
        return expression != null && !expression.isEmpty();
    }

    public boolean isAnyAuthenticatedUser() {
        return anyAuthenticatedUser;
    }

    public void setAnyAuthenticatedUser(boolean anyAuthenticatedUser) {
        this.anyAuthenticatedUser = anyAuthenticatedUser;
    }

    public void validate() {
        if (anyAuthenticatedUser != null && anyAuthenticatedUser && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException("Cannot add user-based access control properties when any-authenticated-user is enabled");
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess() || anyAuthenticatedUser != null)) {
            throw new IllegalArgumentException("Cannot add access control properties to a public repo");
        }

        if (anyAuthenticatedUser == null){
            // disable when at least one authorization option is used
            anyAuthenticatedUser = false;
        }
    }
}
