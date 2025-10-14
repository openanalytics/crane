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
import eu.openanalytics.crane.model.runtime.DeleteTask;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import eu.openanalytics.crane.util.ExecutorServiceFactory;
import org.carlspring.cloud.storage.s3fs.S3Path;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;


@Service
public class DeletionService {
    private final ExecutorService executor = ExecutorServiceFactory.create("DeletionService");
    private record StateEntry(String initiator, long totalFiles) {}
    private final UploadAccessControlService uploadAccessControlService;
    protected UserService userService;
    private final Map<Path, StateEntry> state;

    public DeletionService(UploadAccessControlService uploadAccessControlService, UserService userService) {
        this.uploadAccessControlService = uploadAccessControlService;
        this.userService = userService;
        this.state = new HashMap<>();
    }

    public Map<String, Object> initDeletion(Repository repository, Path path) throws InterruptedException, IOException, IllegalAccessException {
        if (!Files.isDirectory(path)) {
            if (path.toString().startsWith("s3://")) {
                deleteS3Object(path);
            } else if (path.toString().startsWith("/")) {
                boolean deleted = path.toFile().delete();
                if (!deleted) {
                    throw new IOException();
                }
            } else {
                throw new RuntimeException("Path type no supported %s!".formatted(path.toString()));
            }
            return Map.of("message", "File deletion succeeded");
        } else if (Files.isDirectory(path) && !state.containsKey(path)) {
            CountFiles countFiles = new CountFiles(repository);
            Files.walkFileTree(path, countFiles);
            Authentication authentication = userService.getUser();
            DeleteTask deleteTask = new DeleteTask(uploadAccessControlService, repository, path, authentication);
            executor.execute(deleteTask::start);
            StateEntry stateEntry = new StateEntry(authentication.getName(), countFiles.count);
            state.put(path, stateEntry);
        } else if (state.containsKey(path) && !state.get(path).initiator.equals(userService.getUser().getName())) {
            throw new IllegalAccessException("This directory is already being deleted");
        }
        return getProgress(repository, path);
    }

    public Map<String, Object> getProgress(Repository repository, Path path) throws IOException {
        long totalFiles = state.get(path).totalFiles;
        CountFiles remainingFiles = new CountFiles(repository);
        Files.walkFileTree(path, remainingFiles);
        long todo = remainingFiles.count;
        long deleted = totalFiles - remainingFiles.count;
        if (todo == 0) {
            state.remove(path);
        }
        return Map.of("todo", Map.of("files", todo), "deleted", Map.of("files", deleted));
    }

    private class CountFiles extends SimpleFileVisitor<Path> {
        private final Repository repository;
        private long count = 0;
        public CountFiles(Repository repository) {
            this.repository = repository;
        }

        private void count() {
            count += 1;
        }

        @Override
        public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(repository.getName(), dir.toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(repository.getName(), file.toString())) {
                return FileVisitResult.CONTINUE;
            }
            count();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                                               IOException exc) {
            return FileVisitResult.CONTINUE;
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
