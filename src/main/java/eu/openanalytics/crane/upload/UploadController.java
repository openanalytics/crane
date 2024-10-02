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
import eu.openanalytics.crane.model.dto.ApiResponse;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.carlspring.cloud.storage.s3fs.S3Path;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
    private S3TransferManager transferManager;
    private final CraneConfig config;

    public UploadController(CraneConfig config) {
        this.config = config;
    }

    @PostConstruct
    private void init() {
        if (config.usesS3()) {
            S3AsyncClient s3AsyncClient = S3AsyncClient.builder().multipartEnabled(true).build();
            transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();
        }
    }

    @ResponseBody
    @PostMapping(value="/__file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request, HttpServletResponse response) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        if (!isMultipartForm) {
            return ApiResponse.fail(Map.of("message", "Request wasn't a multipart form"));
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        Path path = (Path) request.getAttribute("path");
        if (path == null) {
            return ApiResponse.failNotFound();
        }
        if (Files.exists(path)) {
            return ApiResponse.fail(Map.of("message", "File %s already exists".formatted(path)));
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
