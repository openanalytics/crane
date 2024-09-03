package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.model.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.*;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

@Controller
public class UploadHandler {
    private final CraneConfig config;

    public UploadHandler(CraneConfig config) {
        this.config = config;
    }

    @ResponseBody
    @PostMapping(value="/{repositoryName}/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createResource(MultipartHttpServletRequest request, @PathVariable String repositoryName) {
        boolean isMultipartForm = JakartaServletFileUpload.isMultipartContent(request);

        if (!isMultipartForm) {
            return ApiResponse.fail(Map.of("message", "Request wasn't a multipart form"));
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();

        try {
            for (Iterator<FileItemInput> it = upload.getItemIterator(request).asIterator(); it.hasNext(); ) {
                FileItemInput item = it.next();
                if (!item.isFormField()) {
                    Path path = Path.of(request.getRequestURI());

                    String repositoryStorageLocation = config.getRepository(repositoryName).getStorageLocation();
                    String fileName = item.getName();
                    File file;
                    if (path.endsWith(fileName)) {
                        file = new File(repositoryStorageLocation + path);
                    } else {
                        file = new File(repositoryStorageLocation + path + fileName);
                    }
                    FileUtils.copyInputStreamToFile(item.getInputStream(), file);
                }
            }
            return ApiResponse.success(Map.of("message", "File upload was successful"));
        } catch (IOException e) {
            // TODO
            return ApiResponse.fail(Map.of("message", e.getMessage()));
        }
    }
}
