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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.security.auditing.AbstractAuditingService;
import org.springframework.security.core.Authentication;

public abstract class AbstractAccessControlService {

    private final AbstractPathAccessControlService pathAccessControlService;
    private final AbstractPosixAccessControlService posixAccessControlService;
    private final AbstractAuditingService auditingService;
    private final UserService userService;
    private final CraneConfig craneConfig;

    public AbstractAccessControlService(AbstractPathAccessControlService pathAccessControlService,
                                        AbstractPosixAccessControlService posixAccessControlService,
                                        AbstractAuditingService auditingService,
                                        UserService userService,
                                        CraneConfig craneConfig) {
        this.pathAccessControlService = pathAccessControlService;
        this.posixAccessControlService = posixAccessControlService;
        this.auditingService = auditingService;
        this.userService = userService;
        this.craneConfig = craneConfig;
    }

    public boolean canAccess(String repository, String path) {
        Repository repositoryObject = craneConfig.getRepository(repository);
        if (repositoryObject == null) {
            return false;
        }
        CranePath cranePath = new CranePath(repositoryObject, path);

        boolean canAccess = pathAccessControlService.canAccess(cranePath) && posixAccessControlService.canAccess(cranePath);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(userService.getUser());
        }
        return canAccess;
    }

    public boolean canAccess(CranePath path) {
        boolean canAccess = pathAccessControlService.canAccess(path) && posixAccessControlService.canAccess(path);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(userService.getUser());
        }
        return canAccess;
    }

    public boolean canAccess(CranePath path, Authentication auth) {
        boolean canAccess = pathAccessControlService.canAccess(auth, path) && posixAccessControlService.canAccess(auth, path);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(auth);
        }
        return canAccess;
    }

}
