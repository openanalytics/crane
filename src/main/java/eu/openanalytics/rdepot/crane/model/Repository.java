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
package eu.openanalytics.rdepot.crane.model;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Repository {

    private String name;
    private List<String> accessGroups;
    private List<String> accessUsers;
    private boolean accessAnyAuthenticatedUser;
    private List<String> accessNetwork;
    private List<IpAddressMatcher> accessNetworkMatchers;
    private String accessExpression;
    private Boolean isPublic = false;

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAccessGroups() {
        return accessGroups;
    }

    public void setAccessGroups(List<String> accessGroups) {
        this.accessGroups = accessGroups;
    }

    public List<String> getAccessUsers() {
        return accessUsers;
    }

    public void setAccessUsers(List<String> accessUsers) {
        this.accessUsers = accessUsers;
    }

    public List<String> getAccessNetwork() {
        return accessNetwork;
    }

    public void setAccessNetwork(List<String> accessIpRanges) {
        this.accessNetwork = accessIpRanges;
        this.accessNetworkMatchers = accessIpRanges.stream()
            .map(IpAddressMatcher::new).collect(Collectors.toList());;
    }

    public List<IpAddressMatcher> getAccessNetworkMatchers() {
        return accessNetworkMatchers;
    }

    public String getAccessExpression() {
        return accessExpression;
    }

    public void setAccessExpression(String accessExpression) {
        this.accessExpression = accessExpression;
    }

    public boolean hasGroupAccess() {
        return accessGroups != null && accessGroups.size() > 0;
    }

    public boolean hasUserAccess() {
        return accessUsers != null && accessUsers.size() > 0;
    }

    public boolean hasNetworkAccess() {
        return accessNetwork != null && accessNetwork.size() > 0;
    }

    public boolean hasExpressionAccess() {
        return accessExpression != null && accessExpression.length() > 0;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void setAccessAnyAuthenticatedUser(boolean accessAnyAuthenticatedUser) {
        this.accessAnyAuthenticatedUser = accessAnyAuthenticatedUser;
    }

    public boolean isAccessAnyAuthenticatedUser() {
        return accessAnyAuthenticatedUser;
    }

    public void validate() {
        if (name == null) {
            throw new RuntimeException("Repository has no name");
        }

        // restrict Repository name in order to limit chances of path traversal
        if (!namePattern.matcher(name).matches()) {
            throw new RuntimeException(String.format("Repository name %s contains invalid characters", name));
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add access control properties to a public repo", name));
        }

        if (isAccessAnyAuthenticatedUser() && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add user-based access control properties to a repo that allows any authenticated user", name));
        }

        if (!hasGroupAccess() && !hasUserAccess() && !hasExpressionAccess() && !hasNetworkAccess()) {
            accessAnyAuthenticatedUser = true;
        }
    }

}
