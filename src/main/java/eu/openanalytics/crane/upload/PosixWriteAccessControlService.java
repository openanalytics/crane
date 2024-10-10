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
package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.service.AbstractPosixAccessControlService;
import eu.openanalytics.crane.service.UserService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Iterator;

@Service
public class PosixWriteAccessControlService extends AbstractPosixAccessControlService {
    public PosixWriteAccessControlService(UserService userService, CraneConfig craneConfig) {
        super(userService, craneConfig);
    }

    @Override
    protected PosixFilePermission getOwnerAccess() {
        return PosixFilePermission.OWNER_WRITE;
    }

    @Override
    protected PosixFilePermission getGroupAccess() {
        return PosixFilePermission.GROUP_WRITE;
    }

    @Override
    protected Iterator<Path> getSubsequentPaths(String path) {
        return Path.of(path).getParent().iterator();
    }
}
