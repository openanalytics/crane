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

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CraneDirectory;
import eu.openanalytics.crane.model.runtime.CraneFile;
import eu.openanalytics.crane.model.runtime.CraneResource;
import eu.openanalytics.crane.upload.PathWriteAccessControlService;
import eu.openanalytics.crane.upload.PosixWriteAccessControlService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class IndexPageService {

    private final CraneConfig config;
    private final PathReadAccessControlService pathReadAccessControlService;
    private final PosixReadAccessControlService posixReadAccessControlService;
    private final PathWriteAccessControlService pathWriteAccessControlService;
    private final PosixWriteAccessControlService posixWriteAccessControlService;

    public IndexPageService(CraneConfig config, PathReadAccessControlService pathReadAccessControlService, PosixReadAccessControlService posixReadAccessControlService, PathWriteAccessControlService pathWriteAccessControlService, PosixWriteAccessControlService posixWriteAccessControlService) {
        this.config = config;
        this.pathReadAccessControlService = pathReadAccessControlService;
        this.posixReadAccessControlService = posixReadAccessControlService;
        this.pathWriteAccessControlService = pathWriteAccessControlService;
        this.posixWriteAccessControlService = posixWriteAccessControlService;
    }

    public String getTemplateName(Repository repository) {
        return "default-index";
    }

    public Map<String, Object> getTemplateVariables(Repository repository, Path path) throws IOException {
        List<CraneFile> craneFiles = new ArrayList<>();
        List<CraneDirectory> craneDirectories = new ArrayList<>();
        try (Stream<Path> dirListing = Files.list(path)) {
            dirListing.forEach(p -> {
                CraneResource craneResource = CraneResource.createFromPath(p, repository);
                if (craneResource == null) {
                    // TODO
                    return;
                }
                String fullPath = p.toString().substring(repository.getStorageLocation().length());
                if (pathReadAccessControlService.canAccess(repository, fullPath) && posixReadAccessControlService.canAccess(repository, fullPath)) {
                    if (craneResource instanceof CraneFile craneFile) {
                        craneFiles.add(craneFile);
                    } else if (craneResource instanceof CraneDirectory craneDirectory) {
                        craneDirectories.add(craneDirectory);
                    } else {
                        // TODO
                    }
                }
            });
        }

        // check user write access
        boolean hasWriteAccess = pathWriteAccessControlService.canAccess(repository, path.toString()) && posixWriteAccessControlService.canAccess(repository, path.toString());

        // breadcrumbs
        List<CraneResource> breadcrumbs = new ArrayList<>();
        Path current = path;
        CraneResource resource = CraneResource.createFromPath(current, repository);
        while (resource != null && current != null && !current.toString().equals(repository.getStoragePath().toString())) {
            breadcrumbs.add(0, resource);
            current = current.getParent();
            resource = CraneResource.createFromPath(current, repository);
        }
        breadcrumbs.add(0, resource);

        Map<String, Object> map = new HashMap<>();
        map.put("files", craneFiles);
        map.put("directories", craneDirectories);
        map.put("hasWriteAccess", hasWriteAccess);
        map.put("breadcrumbs", breadcrumbs);
        return map;
    }

}
