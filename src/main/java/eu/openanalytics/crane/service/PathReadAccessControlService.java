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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import org.springframework.stereotype.Service;

@Service
public class PathReadAccessControlService extends AbstractPathAccessControlService {
    public PathReadAccessControlService(SpecExpressionResolver specExpressionResolver, UserService userService, CraneConfig craneConfig) {
        super(specExpressionResolver, userService, craneConfig);
    }

    @Override
    protected AccessControl getAccessControl(PathComponent pathComponent) {
        return pathComponent.getReadAccess();
    }
}
