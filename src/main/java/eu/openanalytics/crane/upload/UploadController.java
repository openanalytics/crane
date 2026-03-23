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
package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.service.DeletionService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Controller
public class UploadController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CraneConfig config;
    private final UploadAuditing auditingService;
    private final DeletionService deletionService;

    public UploadController(CraneConfig config, UploadAuditing auditingService, DeletionService deletionService) {
        this.config = config;
        this.auditingService = auditingService;
        this.deletionService = deletionService;
    }

    @PreAuthorize("@uploadAccessControlService.canAccess(#r, #p)")
    @ResponseBody
    @PostMapping(value = "/__file/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request,
                                                                           @P("r") @PathVariable(name = "repository") String stringRepository,
                                                                           @P("p") @PathVariable(name = "path") String stringPath,
                                                                           @RequestParam(value = "replace", defaultValue = "false") Boolean replace) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        Repository repository = config.getRepository(stringRepository);
        CranePath cranePath = repository.getPath(stringPath);

        if (!isMultipartForm) {
            return createDirectory(request, cranePath);
        }

        if (!replace && cranePath.isExists()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "File %s already exists".formatted(cranePath.getWebUri())));
        }

        if (cranePath.isMetadataSchemaFile()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Cannot override metadata schema"));
        }

        try {
            FileItemInput fileItemInput = getFileItemInput(upload.getItemIterator(request));
            if (fileItemInput == null) {
                auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
                return ApiResponse.fail(Map.of("message", "Upload failed. No parameter named `file` found"));
            }
            CranePath parent = cranePath.getParent();
            if (!parent.isExists()) {
                Files.createDirectories(parent.getStoragePath());
            }
            if (!replace) {
                Files.copy(fileItemInput.getInputStream(), cranePath.getStoragePath());
            } else {
                Files.copy(fileItemInput.getInputStream(), cranePath.getStoragePath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (repository.isPosixAccessControl()) {
                setPathAttribute(cranePath);
            }
            auditingService.createUploadAuditEvent(request);
            return ApiResponse.success(Map.of("message", "File upload succeeded"));
        } catch (IOException e) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "File upload failed"));
        }
    }

    @PreAuthorize("@uploadAccessControlService.canAccess(#r, #p)")
    @ResponseBody
    @DeleteMapping(value = "/__file/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Object>> deleteResource(HttpServletRequest request,
                                                              @P("r") @PathVariable(name = "repository") String stringRepository,
                                                              @P("p") @PathVariable(name = "path") String stringPath) {
        Repository repository = config.getRepository(stringRepository);
        CranePath cranePath = repository.getPath(stringPath);

        if (!cranePath.isExists()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            deletionService.removeFromState(cranePath);
            return ApiResponse.fail(Map.of("message", "File %s doesn't exist".formatted(cranePath.getWebUri())));
        }
        try {
            if (cranePath.isDirectory()) {
                DeletionService.Progress progress = deletionService.initDeletion(cranePath);
                auditingService.createUploadAuditEvent(request);
                return ApiResponse.accepted(progress);
            } else {
                Files.deleteIfExists(cranePath.getStoragePath());
                return ApiResponse.success();
            }
        } catch (NoSuchFileException e) {
            // file or directory deleted in background
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "File %s doesn't exist".formatted(cranePath.getWebUri())));
        } catch (IOException e) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "File deletion failed"));
        } catch (IllegalAccessException e) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.CONFLICT);
            return ApiResponse.failConflict(Map.of("message", e.getMessage()));
        }
    }

    private void setPathAttribute(CranePath cranePath) throws IOException {
        Map<String, Object> pathAttributes = cranePath.getParent().readAttributes("unix:owner,uid,gid,permissions");
        for (String attr : pathAttributes.keySet()) {
            try {
                cranePath.setAttribute("unix:" + attr, pathAttributes.get(attr));
            } catch (IOException e) {
                logger.warn("Crane could not set '{}' unix attribute of '{}'", attr, cranePath);
            }
        }
    }

    private @Nullable FileItemInput getFileItemInput(FileItemInputIterator itemInputIterator) throws IOException {
        while (itemInputIterator.hasNext()) {
            FileItemInput temporaryFileItemInput = itemInputIterator.next();
            if (temporaryFileItemInput.getFieldName().equals("file")) {
                return temporaryFileItemInput;
            }
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> createDirectory(HttpServletRequest request, CranePath cranePath) {
        if (cranePath.isExists()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Directory %s already exists".formatted(cranePath.getWebUri())));
        }
        if (cranePath.getParent().isMetadataDirectory()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Cannot create directories in a metadata directory"));
        }
        try {
            Files.createDirectories(cranePath.getStoragePath());
            if (cranePath.getRepository().isPosixAccessControl()) {
                setPathAttribute(cranePath);
            }
        } catch (SecurityException | IOException e) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Directory creation failed"));
        }
        auditingService.createUploadAuditEvent(request);
        return ApiResponse.success(Map.of("message", "Directory creation succeeded"));
    }

}
