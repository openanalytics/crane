package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.MultipartInput;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.carlspring.cloud.storage.s3fs.S3Path;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

@Controller
public class UploadHandler {
    private final CraneConfig config;

    public UploadHandler(CraneConfig config) {
        this.config = config;
    }

    @ResponseBody
    @PostMapping(value="/__upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(HttpServletRequest request, HttpServletResponse response) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        if (!isMultipartForm) {
            return ApiResponse.fail(Map.of("message", "Request wasn't a multipart form"));
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        try {
            for (Iterator<FileItemInput> it = upload.getItemIterator(request).asIterator(); it.hasNext(); ) {
                FileItemInput item = it.next();
                if (!item.isFormField()) {
                    writeFile(item, request);
                }
            }
            return ApiResponse.success(Map.of("message", "File upload was successful"));
        } catch (IOException e) {
            // TODO
            return ApiResponse.fail(Map.of("message", e.getMessage()));
        }
    }

    private void writeFile(FileItemInput item, HttpServletRequest request) throws IOException {
        Path path = (Path) request.getAttribute("path");
        if (path.toString().startsWith("s3://")) {
            S3Client s3Client = S3Client.create();
            S3Path s3Path = (S3Path) path;
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Path.getBucketName())
                    .key(s3Path.getKey()).build();
            s3Client.putObject(putObjectRequest, RequestBody.fromString(new String(item.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
            s3Client.close();
        } else if (path.toString().startsWith("file://")) {
            FileUtils.copyInputStreamToFile(item.getInputStream(), path.toFile());
        } else {
            throw new RuntimeException("Path type no supported %s".formatted(path.toString()));
        }
    }
}
