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
package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import eu.openanalytics.crane.service.AbstractPosixAccessControlService;
import eu.openanalytics.crane.service.UserService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Controller
public class UploadController {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private S3TransferManager transferManager;
    private final CraneConfig config;
    private final PathWriteAccessControlService pathWriteAccessControlService;
    private final PosixWriteAccessControlService posixWriteAccessControlService;
    private final UserService userService;

    public UploadController(CraneConfig config, PathWriteAccessControlService pathWriteAccessControlService, PosixWriteAccessControlService posixWriteAccessControlService, UserService userService) {
        this.config = config;
        this.pathWriteAccessControlService = pathWriteAccessControlService;
        this.posixWriteAccessControlService = posixWriteAccessControlService;
        this.userService = userService;
    }

    @PostConstruct
    private void init() {
        if (config.usesS3()) {
            S3AsyncClient s3AsyncClient = S3AsyncClient.builder().multipartEnabled(true).build();
            transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();
        }
    }

//    @PreAuthorize("@pathWriteAccessControlService.canAccess(#r, #p) && @posixWriteAccessControlService.canAccess(#r, #p)")
    @ResponseBody
    @PostMapping(value = "/__file/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request,
                                                                           @P("r") @PathVariable(name = "repository") String stringRepository,
                                                                           @P("p") @PathVariable(name = "path") String stringPath) {
        if (!pathWriteAccessControlService.canAccess(stringRepository, stringPath) || !posixWriteAccessControlService.canAccess(stringRepository, stringPath)) {
            return userService.getUser() instanceof AnonymousAuthenticationToken ? ApiResponse.failUnauthorized() : ApiResponse.failForbidden();
        }
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        if (!isMultipartForm) {
            return ApiResponse.fail(Map.of("message", "Request wasn't a multipart form"));
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        Repository repository = config.getRepository(stringRepository);
        Path path = repository.getStoragePath().resolve(stringPath.substring(1));

        if (Files.exists(path)) {
            return ApiResponse.fail(Map.of("message", "File %s already exists".formatted(stringRepository + stringPath)));
        }

        try {
            FileItemInput fileItemInput = getFileItemInput(upload.getItemIterator(request));
            if (fileItemInput == null) {
                return ApiResponse.fail(Map.of("message", "Upload failed. No parameter named `file` found"));
            }
            if (path.toString().startsWith("s3://")) {
                writeFileToS3(fileItemInput, path);
            } else if (path.toString().startsWith("/")) {
                FileUtils.copyInputStreamToFile(fileItemInput.getInputStream(), path.toFile());
                if (repository.hasPosixAccessControl()) {
                    Map<String, Object> pathAttributes = Files.readAttributes(path.getParent(), "unix:owner,uid,gid,permissions");
                    for (String attr : pathAttributes.keySet()) {
                        try {
                            Files.setAttribute(path, "unix:" + attr, pathAttributes.get(attr));
                        } catch (IOException e) {
                            logger.warn("Crane could not set '{}' unix attribute of '{}'", attr, path);
                        }
                    }
                }
            } else {
                throw new RuntimeException("Path type no supported %s!".formatted(path.toString()));
            }
            return ApiResponse.success(Map.of("message", "File upload succeeded"));
        } catch (IOException e) {
            return ApiResponse.fail(Map.of("message", "File upload failed"));
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

    private void writeFileToS3(FileItemInput fileItemInput, Path path) throws IOException {
        BlockingInputStreamAsyncRequestBody body =
            AsyncRequestBody.forBlockingInputStream(null);

        S3Path s3Path = (S3Path) path;
        Upload s3UploadRequest = transferManager.upload(builder -> builder
            .requestBody(body)
            .putObjectRequest(req -> req.bucket(s3Path.getBucketName()).key(s3Path.getKey()))
            .build());

        body.writeInputStream(fileItemInput.getInputStream());
        s3UploadRequest.completionFuture().join();
    }
}
