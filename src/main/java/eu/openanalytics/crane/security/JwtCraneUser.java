/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.security;

import java.util.List;

public class JwtCraneUser implements CraneUser {

    private final int posixUID;
    private final List<Integer> posixGIDs;
    private final String name;

    public JwtCraneUser(String name,
                        int posixUID,
                        List<Integer> posixGIDs) {
        this.posixUID = posixUID;
        this.posixGIDs = posixGIDs;
        this.name = name;
    }

    public List<Integer> getPosixGIDs() {
        return posixGIDs;
    }

    public int getPosixUID() {
        return posixUID;
    }

    @Override
    public String getName() {
        return name;
    }

}
