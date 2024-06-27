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
package eu.openanalytics.rdepot.crane.model.config;

import java.time.Duration;

public class CacheRule {

    private String pattern;
    private Duration maxAge;

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(String maxAge) {

    }

    public void setMaxAge(Duration maxAge) {
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("Incorrect configuration detected: the maxAge of a cache entry must be positive");
        }
        // allow up to one year ( https://stackoverflow.com/a/25201898/1393103 )
        if (maxAge.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("Incorrect configuration detected: the maxAge of a cache entry may not be greater than one year");
        }
        this.maxAge = maxAge;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: the pattern of a cache entry must start with /");
        }
        this.pattern = pattern;
    }

}
