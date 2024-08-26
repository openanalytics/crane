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

import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.crane.service.spel.SpecExpressionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class HandelSpecExpressionService {
    private final UserService userService;
    private final SpecExpressionResolver specExpressionResolver;

    public HandelSpecExpressionService(UserService userService, SpecExpressionResolver specExpressionResolver) {
        this.userService = userService;
        this.specExpressionResolver = specExpressionResolver;
    }

    public boolean handleByOnErrorExpression(Repository repository, HttpServletRequest request, HttpServletResponse response, int errorStatus) {
        if (repository.getOnErrorExpression() == null) {
            return false;
        }
        response.setStatus(errorStatus);
        Authentication auth = userService.getUser();
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), repository, request, response);
        return specExpressionResolver.evaluateToBoolean(repository.getOnErrorExpression(), context);
    }
}
