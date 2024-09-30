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
public class PathComponent extends AccessControl{

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");
    private String name;
    private Map<String, PathComponent> components;
    private AccessControl write;

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
                if (component.getPublic() && !getPublic()) {
                    throw new IllegalArgumentException(String.format("PathComponent %s is invalid, cannot have a public repository (%s) in a private parent (%s)", component.name, component.name, name));
                }
                component.validate();
            }
        }
    }

    public AccessControl getWrite() {
        return write;
    }

    public void setWrite(AccessControl write) {
        this.write = write;
    }
}
