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
package eu.openanalytics.crane.model.converter;

import eu.openanalytics.crane.model.config.PathComponent;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * The PathComponentConverter is required to parse paths that have no properties defined.
 * PathComponents without properties in the application.yml are passed as empty Strings which can't be
 * directly converted to a PathComponent.
 */
@Component
@ConfigurationPropertiesBinding
public class PathComponentConverter implements Converter<String, PathComponent> {

    @Override
    public PathComponent convert(@NotNull String source) {
        return new PathComponent();
    }
}
