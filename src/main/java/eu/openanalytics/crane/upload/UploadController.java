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
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.carlspring.cloud.storage.s3fs.S3Path;
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
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;

import java.io.IOException;
import java.util.Map;

@Controller
public class UploadController {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private S3TransferManager transferManager;
    private final CraneConfig config;
    private final UploadAuditing auditingService;
    private final DeletionService deletionService;

    public UploadController(CraneConfig config, UploadAuditing auditingService, DeletionService deletionService) {
        this.config = config;
        this.auditingService = auditingService;
        this.deletionService = deletionService;
    }

    @PostConstruct
    private void init() {
        if (config.usesS3()) {
            S3AsyncClient s3AsyncClient = S3AsyncClient.builder().multipartEnabled(true).build();
            transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();
        }
    }

    @PreAuthorize("@uploadAccessControlService.canAccess(#r, #p)")
    @ResponseBody
    @PostMapping(value = "/__file/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request,
                                                                           @P("r") @PathVariable(name = "repository") String stringRepository,
                                                                           @P("p") @PathVariable(name = "path") String stringPath) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        Repository repository = config.getRepository(stringRepository);
        CranePath cranePath = repository.getPath(stringPath);

        if (!isMultipartForm) {
            return createDirectory(request, cranePath);
        }

        if (cranePath.isExists()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "File %s already exists".formatted(stringRepository + stringPath)));
        }

        try {
            FileItemInput fileItemInput = getFileItemInput(upload.getItemIterator(request));
            if (fileItemInput == null) {
                auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
                return ApiResponse.fail(Map.of("message", "Upload failed. No parameter named `file` found"));
            }
            if (repository.usesS3())  {
                writeFileToS3(fileItemInput, cranePath);
            } else {
                FileUtils.copyInputStreamToFile(fileItemInput.getInputStream(), cranePath.getStorageFile());
                if (repository.hasPosixAccessControl()) {
                    setPathAttribute(cranePath);
                }
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
            return ApiResponse.fail(Map.of("message", "File %s doesn't exist".formatted(stringRepository + stringPath)));
        }
        try {
            DeletionService.Progress progress = deletionService.initDeletion(repository, cranePath);
            auditingService.createUploadAuditEvent(request);
            return ApiResponse.accepted(progress);
        } catch (InterruptedException e) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Directory deletion was interrupted"));
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

    private void writeFileToS3(FileItemInput fileItemInput, CranePath path) throws IOException {
        BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(null);

        S3Path s3Path = (S3Path) path.getStoragePath();
        Upload s3UploadRequest = transferManager.upload(builder -> builder
            .requestBody(body)
            .putObjectRequest(req -> req.bucket(s3Path.getBucketName()).key(s3Path.getKey()))
            .build());

        body.writeInputStream(fileItemInput.getInputStream());
        s3UploadRequest.completionFuture().join();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> createDirectory(HttpServletRequest request, CranePath cranePath) {
        if (cranePath.isExists()) {
            auditingService.createErrorHandlerAuditEvent(request, HttpStatus.BAD_REQUEST);
            return ApiResponse.fail(Map.of("message", "Directory %s already exists".formatted(cranePath)));
        }
        try {
            if (cranePath.getRepository().usesS3()) {
                logger.warn("S3 Directory creation is not supported");
                return ApiResponse.fail(Map.of("message", "S3 Directory creation is not supported"));
            } else {
                boolean createdDirectory = cranePath.getStorageFile().mkdirs();
                if (!createdDirectory) {
                    auditingService.createUploadAuditEvent(request);
                    return ApiResponse.success(Map.of("message", "Directory creation failed"));
                }
                if (cranePath.getRepository().hasPosixAccessControl()) {
                    setPathAttribute(cranePath);
                }
            }
        } catch (SecurityException | IOException e) {
            auditingService.createUploadAuditEvent(request);
            return ApiResponse.success(Map.of("message", "Directory creation failed"));
        }
        auditingService.createUploadAuditEvent(request);
        return ApiResponse.success(Map.of("message", "Directory creation succeeded"));
    }
}
