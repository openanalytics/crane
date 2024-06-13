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
package eu.openanalytics.rdepot.crane.model.config;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/***
 * Representation of a single component in a path, containing access-control and containing sub-components.
 * E.g. in /my/path/filename, `my`, `path` and `filename` are all a component represented by this class.
 * In order to represent a filesystem tree with access-control, this class contains sub-components.
 */
public class PathComponent {

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");

    private String name;
    private List<String> accessGroups;
    private List<String> accessUsers;
    private boolean accessAnyAuthenticatedUser;
    private List<String> accessNetwork;
    private List<IpAddressMatcher> accessNetworkMatchers;
    private String accessExpression;
    private Map<String, PathComponent> components;
    private boolean isPublic = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PathComponent> getPaths() {
        if (components != null) {
            return new ArrayList<>(components.values());
        }
        return null;
    }

    public void setPaths(List<PathComponent> paths) {
        this.components = paths.stream().collect(Collectors.toMap(PathComponent::getName, p -> p));
    }

    public boolean hasPaths() {
        return components != null && !components.isEmpty();
    }

    public Optional<PathComponent> getPath(String name) {
        return Optional.ofNullable(components.get(name));
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
            .map(IpAddressMatcher::new).collect(Collectors.toList());
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

    public void setAccessAnyAuthenticatedUser(boolean accessAnyAuthenticatedUser) {
        this.accessAnyAuthenticatedUser = accessAnyAuthenticatedUser;
    }

    public boolean isAccessAnyAuthenticatedUser() {
        return accessAnyAuthenticatedUser;
    }

    public boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }


    public void validate() {
        if (getName() == null) {
            throw new RuntimeException("PathComponent has no name");
        }

        // restrict Repository name in order to limit chances of path traversal
        if (!namePattern.matcher(getName()).matches()) {
            throw new RuntimeException(String.format("PathComponent name %s contains invalid characters", name));
        }

        if (isAccessAnyAuthenticatedUser() && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException(String.format("PathComponent %s is invalid, cannot add user-based access control properties to a repo that allows any authenticated user", name));
        }

        if (!hasGroupAccess() && !hasUserAccess() && !hasExpressionAccess() && !hasNetworkAccess()) {
            accessAnyAuthenticatedUser = true;
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add access control properties to a public repo", getName()));
        }

        if (components != null) {
            for (PathComponent component : components.values()) {
                component.validate();
            }
        }
    }

}
