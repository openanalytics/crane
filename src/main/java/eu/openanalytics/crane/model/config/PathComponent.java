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


import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/***
 * Representation of a single component in a path, containing access-control and containing sub-components.
 * E.g. in /my/path/filename, `my`, `path` and `filename` are all a component represented by this class.
 * In order to represent a filesystem tree with access-control, this class contains sub-components.
 */
public class PathComponent {

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");

    private final Map<String, PathComponent> components;

    @Getter
    private String name;

    @Getter
    private final AccessControl readAccess;

    @Getter
    private final AccessControl writeAccess;

    @Getter
    private final AccessControl metadataReadAccess;

    @Getter
    private final AccessControl metadataWriteAccess;

    public PathComponent(AccessControl readAccess,
                         AccessControl writeAccess,
                         AccessControl metadataReadAccess,
                         AccessControl metadataWriteAccess,
                         Map<String, PathComponent> paths) {
        if (readAccess == null && writeAccess == null && metadataReadAccess == null && metadataWriteAccess == null) {
            throw new IllegalArgumentException("PathComponent is invalid: no access control specified");
        }
        this.readAccess = Objects.requireNonNullElse(readAccess, new AccessControl());
        this.writeAccess = Objects.requireNonNullElse(writeAccess, new AccessControl());
        this.metadataReadAccess = Objects.requireNonNullElse(metadataReadAccess, new AccessControl());
        this.metadataWriteAccess = Objects.requireNonNullElse(metadataWriteAccess, new AccessControl());
        if (paths != null) {
            paths.forEach((name, pathComponent) -> pathComponent.setName(name));
        }
        this.components = Objects.requireNonNullElse(paths, Map.of());

    }

    public void setName(String name) {
        if (this.name != null) {
            throw new IllegalStateException("Name already set");
        }
        this.name = name;
    }

    public List<PathComponent> getPaths() {
        if (components != null) {
            return new ArrayList<>(components.values());
        }
        return null;
    }

    public boolean hasPaths() {
        return components != null && !components.isEmpty();
    }

    public Optional<PathComponent> getPathComponent(String name) {
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

        validateReadAccess(onlyPublic);
        validateWriteAccess(onlyPublic);

        if (components != null) {
            for (PathComponent component : components.values()) {
                component.validate(onlyPublic);
                if (component.readAccess.isPublic() && !readAccess.isPublic()) {
                    throw new IllegalArgumentException(String.format("PathComponent %s has invalid read access control: cannot have a public PathComponent (%s) in a private parent (%s)", component.name, component.name, name));
                }

                if (component.writeAccess.isPublic() && !writeAccess.isPublic()) {
                    throw new IllegalArgumentException(String.format("PathComponent %s has invalid write access control: cannot have a public PathComponent (%s) in a private parent (%s)", component.name, component.name, name));
                }
            }
        }
    }

    protected void validateReadAccess(boolean onlyPublic) {
        if (onlyPublic && readAccess != null && !readAccess.isPublic()) {
            throw new IllegalArgumentException("The repository `%s` should have public read access when the `only-public` property is `true`.".formatted(name));
        }
    }

    protected void validateWriteAccess(boolean onlyPublic) {
        if (onlyPublic && writeAccess != null && !writeAccess.isPublic() && !writeAccess.equals(new AccessControl())) {
            throw new IllegalArgumentException("The repository `%s` should not have write access defined (or public) when the `only-public` property is `true`.".formatted(name));
        }
    }

    protected void init(Path storagePath) throws IOException {
        Path path = storagePath.resolve(getName());
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        if (hasPaths()) {
            for (PathComponent pathComponent : getPaths()) {
                pathComponent.init(path);
            }
        }
    }

}
