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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CraneDirectory;
import eu.openanalytics.crane.model.runtime.CraneFile;
import eu.openanalytics.crane.model.runtime.CraneResource;
import eu.openanalytics.crane.upload.UploadAccessControlService;
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

    private final ReadAccessControlService readAccessControlService;
    private final UploadAccessControlService uploadAccessControlService;

    public IndexPageService(ReadAccessControlService readAccessControlService, UploadAccessControlService uploadAccessControlService) {
        this.readAccessControlService = readAccessControlService;
        this.uploadAccessControlService = uploadAccessControlService;
    }

    public String getTemplateName(Repository repository) {
        return "default-index";
    }

    public Map<String, Object> getTemplateVariables(Repository repository, Path path) throws IOException {
        List<CraneFile> craneFiles = new ArrayList<>();
        List<CraneDirectory> craneDirectories = new ArrayList<>();
        try (Stream<Path> dirListing = Files.list(path)) {
            dirListing.forEach(p -> {
                CraneResource craneResource = CraneResource.createFromPath(p, repository, uploadAccessControlService);
                if (craneResource == null) {
                    // TODO
                    return;
                }
                String fullPath = p.toString().substring(repository.getStorageLocation().length());
                if (readAccessControlService.canAccess(repository, fullPath)) {
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
        boolean hasWriteAccess = uploadAccessControlService.canAccess(repository, path.toString());

        // breadcrumbs
        List<CraneResource> breadcrumbs = new ArrayList<>();
        Path current = path;
        CraneResource resource = CraneResource.createFromPath(current, repository, uploadAccessControlService);
        while (resource != null && current != null && !current.toString().equals(repository.getStoragePath().toString())) {
            breadcrumbs.add(0, resource);
            current = current.getParent();
            resource = CraneResource.createFromPath(current, repository, uploadAccessControlService);
        }
        // add breadcrumb for the repository: always use the name of the repository, not the name of the directory in the storage-location
        breadcrumbs.add(0,new CraneDirectory(repository.getName(), "/" + repository.getName(), true, false));

        Map<String, Object> map = new HashMap<>();
        map.put("files", craneFiles);
        map.put("directories", craneDirectories);
        map.put("hasWriteAccess", hasWriteAccess);
        map.put("breadcrumbs", breadcrumbs);
        return map;
    }

}
