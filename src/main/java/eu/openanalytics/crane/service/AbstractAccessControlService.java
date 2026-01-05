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

import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.security.auditing.AbstractAuditingService;
import org.springframework.security.core.Authentication;

public abstract class AbstractAccessControlService {
    protected AbstractPathAccessControlService pathAccessControlService;
    protected AbstractPosixAccessControlService posixAccessControlService;
    protected AbstractAuditingService auditingService;
    protected UserService userService;

    public boolean canAccess(Repository repository, String path) {
        boolean canAccess = pathAccessControlService.canAccess(repository, path) && posixAccessControlService.canAccess(repository, path);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(userService.getUser());
        }
        return canAccess;
    }

    public boolean canAccess(String repository, String path) {
        boolean canAccess = pathAccessControlService.canAccess(repository, path) && posixAccessControlService.canAccess(repository, path);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(userService.getUser());
        }
        return canAccess;
    }

    public boolean canAccess(Repository repository, String path, Authentication auth) {
        boolean canAccess = pathAccessControlService.canAccess(auth, path, repository) && posixAccessControlService.canAccess(auth, path, repository);
        if (!canAccess) {
            auditingService.createAuthorizationDeniedEvent(auth);
        }
        return canAccess;
    }
}
