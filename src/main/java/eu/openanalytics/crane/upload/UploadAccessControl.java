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

import eu.openanalytics.crane.model.config.AccessControl;

public class UploadAccessControl extends AccessControl {
    @Override
    public void validate(String name) {
        if (isAnyAuthenticatedUser() && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException(String.format("PathComponent %s is invalid, cannot add user-based write access control properties to a repo that allows any authenticated user", name));
        }

        if (!hasGroupAccess() && !hasUserAccess() && !hasExpressionAccess() && !hasNetworkAccess()) {
            anyAuthenticatedUser = false;
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add write access control properties to a public repo", name));
        }
    }
}
