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
package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Service
public class PosixAccessControlService {
    private final CraneConfig config;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PosixAccessControlService(CraneConfig config) {
        this.config = config;
    }

    public boolean canAccess(Authentication auth, PathComponent pathComponent) {
        if (!config.isPosixAccessControl() || auth == null || pathComponent == null) {
            return false;
        }

        PosixFileAttributes attributes;
        try {
            attributes = pathComponent.getPosixFileAttributeView();
        } catch (IOException e) {
            logger.warn(String.format("Could not check POSIX file system permissions of %s", pathComponent.getPosixPath()), e);
            return false;
        }

        Set<PosixFilePermission> permissionSet = attributes.permissions();
        if (attributes.owner().getName().equals(auth.getName()) && permissionSet.contains(PosixFilePermission.OWNER_READ)) {
            return true;
        } else if (permissionSet.contains(PosixFilePermission.GROUP_READ) && isMember(auth, attributes.group().getName())){
            return true;
        } else if (permissionSet.contains(PosixFilePermission.OTHERS_READ)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuthority: auth.getAuthorities()) {
            String groupName = grantedAuthority.getAuthority().toUpperCase();
            if (groupName.startsWith("ROLE_")) {
                groupName = groupName.substring(5);
            }
            if (groupName.equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false;
    }
}
