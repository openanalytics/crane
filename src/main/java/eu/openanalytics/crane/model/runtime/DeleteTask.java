package eu.openanalytics.crane.model.runtime;

import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import org.apache.commons.lang3.NotImplementedException;
import org.carlspring.cloud.storage.s3fs.S3Path;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DeleteTask extends SimpleFileVisitor<Path> {

    private final UploadAccessControlService uploadAccessControlService;
//    private final S3Client client;
    private final Repository repository;
    private final Path path;
    private final Authentication auth;

    public DeleteTask(UploadAccessControlService uploadAccessControlService, Repository repository, Path path, Authentication auth) {
        this.uploadAccessControlService = uploadAccessControlService;
//        this.client = S3Client.create();
        this.repository = repository;
        this.path = path;
        this.auth = auth;
    }

    public boolean start() {
        System.out.println("Starting");
        try {
            Files.walkFileTree(path, this);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(repository, "/" + repository.getStoragePath().relativize(dir).toString(), auth)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exception) {
        delete(dir);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(repository, "/" + repository.getStoragePath().relativize(file).toString(), auth)) {
            return FileVisitResult.CONTINUE;
        }
        delete(file);
        return FileVisitResult.CONTINUE;
    }

    private void delete(Path path) {
        if (path.toString().startsWith("s3://")) {
            throw new NotImplementedException("S3 directory deletion not implemented yet");
        } else if (path.toString().startsWith("/")) {
            path.toFile().delete();
        } else {
            throw new RuntimeException("Path type no supported %s!".formatted(path.toString()));
        }
    }

    private void deleteS3Object(Path path) {
        S3Path s3Path = (S3Path) path;
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(s3Path.getBucketName())
            .key(s3Path.getKey())
            .build();

//        client.deleteObject(deleteObjectRequest);
    }
}
