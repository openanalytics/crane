package eu.openanalytics.crane.model.runtime;

import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.service.DeletionService;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DeleteTask extends SimpleFileVisitor<Path> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UploadAccessControlService uploadAccessControlService;
    private final DeletionService deletionService;
    private final Repository repository;
    private final Path path;
    private final Authentication auth;

    public DeleteTask(DeletionService deletionService, UploadAccessControlService uploadAccessControlService, Repository repository, Path path, Authentication auth) {
        this.deletionService = deletionService;
        this.uploadAccessControlService = uploadAccessControlService;
        this.repository = repository;
        this.path = path;
        this.auth = auth;
    }

    public void startDeletion() {
        try {
            Files.walkFileTree(path, this);
            deletionService.getProgress(repository, path);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(repository, "/" + repository.getStoragePath().relativize(dir), auth)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exception) {
        if (!CraneResource.isPathComponent(dir, repository)) {
            delete(dir);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(repository, "/" + repository.getStoragePath().relativize(file), auth)) {
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
}
