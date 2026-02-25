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
package eu.openanalytics.crane.stat.matomo;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.matomo.java.tracking.MatomoRequest;
import org.matomo.java.tracking.MatomoTracker;
import org.matomo.java.tracking.servlet.JakartaHttpServletWrapper;
import org.matomo.java.tracking.servlet.ServletMatomoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MatomoTrackerFilter extends OncePerRequestFilter {

    private final MatomoTracker tracker;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final RequestMatcher REQUEST_MATCHER = new NegatedRequestMatcher(new OrRequestMatcher(
        new AntPathRequestMatcher("/__assets/**"),
        new AntPathRequestMatcher("/favicon.ico"),
        new AntPathRequestMatcher("/__file/**"),
        new AntPathRequestMatcher("/__index"),
        new AntPathRequestMatcher("/__user/**"),
        new AntPathRequestMatcher("/__language/**")
    ));

    public MatomoTrackerFilter(MatomoTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain) throws ServletException, IOException {
        if (REQUEST_MATCHER.matches(request)) {
            MatomoRequest matomoRequest = ServletMatomoRequest.fromServletRequest(JakartaHttpServletWrapper.fromHttpServletRequest(request)).build();
            logger.debug("Sending matomo request {}", matomoRequest);
            tracker.sendRequestAsync(matomoRequest);
        }
        filterChain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        if (tracker != null) {
            try {
                tracker.close();
            } catch (Exception e) {
                throw new RuntimeException("Could not close tracker", e);
            }
        }
    }

}
