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
import eu.openanalytics.crane.model.runtime.CranePath;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class IndexPageService {

    private final ReadAccessControlService readAccessControlService;
    private final UserService userService;

    public IndexPageService(ReadAccessControlService readAccessControlService, UserService userService) {
        this.readAccessControlService = readAccessControlService;
        this.userService = userService;
    }

    public String getTemplateName(Repository repository) {
        return "default-index";
    }

    public Pair<List<CranePath>, List<CranePath>> getFilesAndDirectoriesInPath(CranePath path) {
        List<CranePath> craneFiles = new ArrayList<>();
        List<CranePath> craneDirectories = new ArrayList<>();

        Authentication auth = userService.getUser();
        try (Stream<CranePath> dirListing = path.getContents()) {
            dirListing.forEach(p -> {
                if (readAccessControlService.canAccess(p, auth)) {
                    if (p.isDirectory()) {
                        craneDirectories.add(p);
                    } else {
                        craneFiles.add(p);
                    }
                }
            });
        }

        return Pair.of(craneFiles, craneDirectories);
    }

}
