package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.model.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.carlspring.cloud.storage.s3fs.S3Path;
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
import java.nio.file.StandardOpenOption;
import java.util.Map;

@Controller
public class UploadHandler {
    private S3TransferManager transferManager;
    private S3AsyncClient s3AsyncClient;

    @ResponseBody
    @PostMapping(value="/__upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request, HttpServletResponse response) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        if (!isMultipartForm) {
            return ApiResponse.fail(Map.of("message", "Request wasn't a multipart form"));
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        Path path = (Path) request.getAttribute("path");
        if (Files.exists(path)) {
            return ApiResponse.fail(Map.of("message", "File %s already exists".formatted(path)));
        }

        try {
            if (path.toString().startsWith("s3://")) {
                writeFileToS3(upload, request);
            } else if (path.toString().startsWith("/")) {
                writeFileToLocalFileSystem(upload, request);
            } else {
                throw new RuntimeException("Path type no supported %s!".formatted(path.toString()));
            }
            return ApiResponse.success(Map.of("message", "File upload was successful"));
        } catch (IOException e) {
            // TODO
            return ApiResponse.fail(Map.of("message", e.getMessage()));
        }
    }

    private void writeFileToLocalFileSystem(JakartaServletFileUpload upload, HttpServletRequest request) throws IOException {
        FileItemInputIterator fileItemInputIterator = upload.getItemIterator(request);
        if (fileItemInputIterator.hasNext()) {
            Path path = (Path) request.getAttribute("path");
            while (fileItemInputIterator.hasNext()) {
                FileItemInput fileItemInput = fileItemInputIterator.next();
                Files.write(path, fileItemInput.getInputStream().readAllBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        }
    }

    private void writeFileToS3(JakartaServletFileUpload upload, HttpServletRequest request) throws IOException {
        FileItemInputIterator fileItemInputIterator = upload.getItemIterator(request);
        if (fileItemInputIterator.hasNext()) {
            BlockingInputStreamAsyncRequestBody body =
                    AsyncRequestBody.forBlockingInputStream(null); // 'null' indicates a stream will be provided later.

            S3Path s3Path = (S3Path) request.getAttribute("path");
            Upload s3UploadRequest = getTransferManager().upload(builder -> builder
                    .requestBody(body)
                    .putObjectRequest(req -> req.bucket(s3Path.getBucketName()).key(s3Path.getKey()))
                    .build());

            while (fileItemInputIterator.hasNext()) {
                FileItemInput fileItemInput = fileItemInputIterator.next();
                if (!fileItemInput.isFormField()) {
                    body.writeInputStream(fileItemInput.getInputStream());
                }
            }
            s3UploadRequest.completionFuture().join();
        }
    }

    private S3TransferManager getTransferManager() {
        if (transferManager == null) {
            transferManager = S3TransferManager.builder().s3Client(getAsyncClient()).build();
        }
        return transferManager;
    }

    private S3AsyncClient getAsyncClient() {
        if (s3AsyncClient == null) {
            s3AsyncClient = S3AsyncClient.builder().multipartEnabled(true).build();
        }
        return s3AsyncClient;
    }
}
