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
package eu.openanalytics.crane;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.service.UserService;
import org.springframework.ui.ModelMap;

public class BaseUIController {

    protected final UserService userService;
    protected final CraneConfig config;

    public BaseUIController(UserService userService, CraneConfig craneConfig) {
        this.userService = userService;
        this.config = craneConfig;
    }

    protected void prepareMap(ModelMap map) {
        boolean authenticated = userService.isAuthenticated();
        map.put("loginUrl", userService.getLoginUrl());
        map.put("logo", config.getLogoUrl());
        map.put("authenticated", authenticated);
        if (authenticated) {
            map.put("username", userService.getUser().getName());
        }
    }
}
