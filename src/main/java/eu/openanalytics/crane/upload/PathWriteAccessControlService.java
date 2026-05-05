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
package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.service.AbstractPathAccessControlService;
import eu.openanalytics.crane.service.UserService;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import org.springframework.stereotype.Service;

@Service
public class PathWriteAccessControlService extends AbstractPathAccessControlService {

    public PathWriteAccessControlService(SpecExpressionResolver specExpressionResolver, UserService userService, CraneConfig craneConfig) {
        super(specExpressionResolver, userService, craneConfig);
    }

    @Override
    protected AccessControl getAccessControl(CranePath fullPath, Repository repository) {
        if (fullPath.isDotCraneDirectory() || fullPath.isMetadataDirectoryOrFile() || fullPath.isExtractedMetadataDirectoryOrFile()) {
            return repository.getMetadataWriteAccess();
        }
        return repository.getWriteAccess();
    }

    @Override
    protected AccessControl getAccessControl(CranePath fullPath, CranePath pathComponent) {
        if (fullPath.isDotCraneDirectory() || fullPath.isMetadataDirectoryOrFile() || fullPath.isExtractedMetadataDirectoryOrFile()) {
            return pathComponent.getMetadataWriteAccess();
        }
        return pathComponent.getWriteAccess();
    }

}
