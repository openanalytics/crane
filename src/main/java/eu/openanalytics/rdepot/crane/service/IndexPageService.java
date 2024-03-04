/**
 * Crane
 *
 * Copyright (C) 2021-2022 Open Analytics
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
package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.model.runtime.CraneDirectory;
import eu.openanalytics.rdepot.crane.model.runtime.CraneFile;
import eu.openanalytics.rdepot.crane.model.runtime.CraneResource;
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
    private final PathAccessControlService pathAccessControlService;

    public IndexPageService(CraneConfig config, PathAccessControlService pathAccessControlService) {
        this.config = config;
        this.pathAccessControlService = pathAccessControlService;
    }

    public String getTemplateName(Repository repository) {
        return "default-index";
    }

    public Map<String, Object> getTemplateVariables(Repository repository, Path path) throws IOException {
        List<CraneFile> craneFiles = new ArrayList<>();
        List<CraneDirectory> craneDirectories = new ArrayList<>();

        try (Stream<Path> dirListing = Files.list(path)) {
            dirListing.forEach(p -> {
                CraneResource craneResource = CraneResource.createFromPath(p, config);
                if (craneResource == null) {
                    // TODO
                    return;
                }
                if (craneResource instanceof CraneFile) {
                    craneFiles.add((CraneFile) craneResource);
                } else if (craneResource instanceof CraneDirectory) {
                    CraneDirectory craneDirectory = (CraneDirectory) craneResource;
                    if (pathAccessControlService.canAccess(repository, craneDirectory)) {
                        craneDirectories.add(craneDirectory);
                    }
                } else {
                    // TODO
                }
            });
        }

        // breadcrumbs
        List<CraneResource> breadcrumbs = new ArrayList<>();
        Path current = path;
        while (current != null && !current.toString().equals(config.getRoot().toString())) {
            breadcrumbs.add(0, CraneResource.createFromPath(current, config));
            current = current.getParent();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("files", craneFiles);
        map.put("directories", craneDirectories);
        map.put("breadcrumbs", breadcrumbs);
        return map;
    }

}
