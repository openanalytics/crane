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
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;


@Service
public class DeletionService {
    private final ExecutorService executor = ExecutorServiceFactory.create("DeletionService");
    private final UploadAccessControlService uploadAccessControlService;
    private final UserService userService;
    private final ConcurrentMap<Path, StateEntry> state;

    public DeletionService(UploadAccessControlService uploadAccessControlService, UserService userService) {
        this.uploadAccessControlService = uploadAccessControlService;
        this.userService = userService;
        this.state = new ConcurrentHashMap<>();
    }

    public Progress initDeletion(Repository repository, Path path) throws InterruptedException, IOException, IllegalAccessException {
        if (!state.containsKey(path)) {
            CountFiles countFiles = new CountFiles(repository, uploadAccessControlService);
            Files.walkFileTree(path, countFiles);
            Authentication authentication = userService.getUser();
            DeleteTask deleteTask = new DeleteTask(this, uploadAccessControlService, repository, path, authentication);
            executor.execute(deleteTask::startDeletion);
            StateEntry stateEntry = new StateEntry(authentication.getName(), countFiles.count);
            state.put(path, stateEntry);
        } else if (state.containsKey(path) && !state.get(path).initiator.equals(userService.getUser().getName())) {
            throw new IllegalAccessException("This directory is already being deleted");
        }
        return getProgress(repository, path);
    }

    public Progress getProgress(Repository repository, Path path) throws IOException {
        if (state.containsKey(path)) {
            long totalFiles = state.get(path).totalFiles;
            CountFiles remainingFiles = new CountFiles(repository, uploadAccessControlService);
            Files.walkFileTree(path, remainingFiles);
            long todo = remainingFiles.count;
            long deleted = totalFiles - remainingFiles.count;
            if (todo == 0) {
                state.remove(path);
            }
            return new Progress(todo, deleted);
        } else {
            throw new IOException("File %s doesn't exist".formatted(path.toString()));
        }
    }

    public void removeFromState(Path path) {
        state.remove(path);
    }

    private static class CountFiles extends SimpleFileVisitor<Path> {
        private final Repository repository;
        private final UploadAccessControlService uploadAccessControlService;
        private long count = 0;
        public CountFiles(Repository repository, UploadAccessControlService uploadAccessControlService) {
            this.repository = repository;
            this.uploadAccessControlService = uploadAccessControlService;
        }

        private void count() {
            count += 1;
        }

        @Override
        public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(repository, dir.toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(repository, file.toString())) {
                return FileVisitResult.CONTINUE;
            }
            count();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFileFailed(Path file,
                                                        @NotNull IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
    private record StateEntry(String initiator, long totalFiles) {}
    public record Progress(long todo, long deleted) {}
}
