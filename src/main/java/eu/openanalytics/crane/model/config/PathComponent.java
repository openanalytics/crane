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
package eu.openanalytics.crane.model.config;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/***
 * Representation of a single component in a path, containing access-control and containing sub-components.
 * E.g. in /my/path/filename, `my`, `path` and `filename` are all a component represented by this class.
 * In order to represent a filesystem tree with access-control, this class contains sub-components.
 */
public class PathComponent {

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");
    private String name;
    private Map<String, PathComponent> components;

    private AccessControl readAccess = null;
    private AccessControl writeAccess = null;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccessControl getReadAccess() {
        return readAccess;
    }

    public void setReadAccess(AccessControl readAccess) {
        this.readAccess = readAccess;
    }

    public List<PathComponent> getPaths() {
        if (components != null) {
            return new ArrayList<>(components.values());
        }
        return null;
    }

    public void setPaths(Map<String, PathComponent> paths) {
        paths.forEach((name, pathComponent) -> pathComponent.setName(name));
        this.components = paths;
    }

    public boolean hasPaths() {
        return components != null && !components.isEmpty();
    }

    public Optional<PathComponent> getPath(String name) {
        return Optional.ofNullable(components.get(name));
    }

    public void validate(boolean onlyPublic) {
        if (getName() == null) {
            throw new RuntimeException("PathComponent has no name");
        }

        // restrict Repository name in order to limit chances of path traversal
        if (!namePattern.matcher(getName()).matches()) {
            throw new RuntimeException(String.format("PathComponent name %s contains invalid characters", name));
        }

        if (readAccess == null && writeAccess == null) {
            throw new IllegalArgumentException(String.format("PathComponent %s is invalid: no access control specified", name));
        }

        validateReadAccess(onlyPublic);
        validateWriteAccess(onlyPublic);

        try {
            readAccess.validate();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(String.format("PathComponent %s has invalid read access control: %s", name, ex.getMessage()));
        }

        try{
            writeAccess.validate();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(String.format("PathComponent %s has invalid write access control: %s", name, ex.getMessage()));
        }
        if (components != null) {
            for (PathComponent component : components.values()) {
                component.validate(onlyPublic);
                if (component.readAccess.getPublic() && !readAccess.getPublic()) {
                    throw new IllegalArgumentException(String.format("PathComponent %s has invalid read access control: cannot have a public PathComponent (%s) in a private parent (%s)", component.name, component.name, name));
                }

                if (component.writeAccess.getPublic() && !writeAccess.getPublic()) {
                    throw new IllegalArgumentException(String.format("PathComponent %s has invalid write access control: cannot have a public PathComponent (%s) in a private parent (%s)", component.name, component.name, name));
                }
            }
        }
    }

    protected void validateReadAccess(boolean onlyPublic) {
        if (onlyPublic && readAccess != null && !readAccess.getPublic()) {
            throw new IllegalArgumentException("The repository `%s` should have public read access when the `only-public` property is `true`.".formatted(name));
        }
        if (readAccess == null) {
            readAccess = new AccessControl();
        }
    }

    protected void validateWriteAccess(boolean onlyPublic) {
        if (onlyPublic && writeAccess != null && !writeAccess.getPublic()) {
            throw new IllegalArgumentException("The repository `%s` should not have write access defined (or public) when the `only-public` property is `true`.".formatted(name));
        }
        if (writeAccess == null) {
            writeAccess = new AccessControl();
        }
    }

    public AccessControl getWriteAccess() {
        return writeAccess;
    }

    public void setWriteAccess(AccessControl writeAccess) {
        this.writeAccess = writeAccess;
    }
}
