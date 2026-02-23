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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.model.runtime.DeleteTask;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import eu.openanalytics.crane.util.ExecutorServiceFactory;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DeletionService {

    private final ExecutorService executor = ExecutorServiceFactory.create("DeletionService");
    private final UploadAccessControlService uploadAccessControlService;
    private final UserService userService;
    private final ConcurrentMap<CranePath, StateEntry> state;

    public DeletionService(UploadAccessControlService uploadAccessControlService, UserService userService) {
        this.uploadAccessControlService = uploadAccessControlService;
        this.userService = userService;
        this.state = new ConcurrentHashMap<>();
    }

    synchronized public Progress initDeletion(CranePath cranePath) throws IOException, IllegalAccessException {
        if (!state.containsKey(cranePath)) {
            CountFiles countFiles = new CountFiles(uploadAccessControlService);
            cranePath.walkFileTree(countFiles);

            Authentication authentication = userService.getUser();

            StateEntry stateEntry = new StateEntry(
                authentication.getName(),
                countFiles.getFiles().size() + countFiles.getDirectories().size(),
                new AtomicLong(0),
                countFiles.getFiles(),
                countFiles.getDirectories());

            DeleteTask deleteTask = new DeleteTask(this, cranePath, stateEntry);
            executor.execute(deleteTask::startDeletion);

            state.put(cranePath, stateEntry);
        } else if (state.containsKey(cranePath) && !state.get(cranePath).initiator.equals(userService.getUser().getName())) {
            throw new IllegalAccessException("This directory is already being deleted");
        }
        return getProgress(cranePath);
    }

    public Progress getProgress(CranePath path) throws IOException {
        if (state.containsKey(path)) {
            StateEntry stateEntry = state.get(path);
            return new Progress(stateEntry.totalFiles, stateEntry.deleted.get());
        } else {
            throw new IOException("File %s doesn't exist".formatted(path.toString()));
        }
    }

    public void removeFromState(CranePath cranePath) {
        state.remove(cranePath);
    }

    public static class CountFiles extends SimpleFileVisitor<CranePath> {

        private final UploadAccessControlService uploadAccessControlService;

        @Getter
        private final List<Path> directories;

        @Getter
        private final List<Path> files;

        public CountFiles(UploadAccessControlService uploadAccessControlService) {
            this.uploadAccessControlService = uploadAccessControlService;
            this.directories = new ArrayList<>();
            this.files = new ArrayList<>();
        }

        @Override
        public @NotNull FileVisitResult preVisitDirectory(@NotNull CranePath dir, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult postVisitDirectory(@NotNull CranePath dir, @Nullable IOException exc) {
            if (!dir.isExistsInConfig()) {
                directories.add(dir.getStoragePath());
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(@NotNull CranePath file, @NotNull BasicFileAttributes attributes) {
            if (!uploadAccessControlService.canAccess(file)) {
                return FileVisitResult.CONTINUE;
            }
            files.add(file.getStoragePath());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFileFailed(@NotNull CranePath file, @NotNull IOException exc) {
            return FileVisitResult.CONTINUE;
        }

    }

    public record StateEntry(String initiator, long totalFiles, AtomicLong deleted, List<Path> files, List<Path> directories) {
    }

    public record Progress(long total, long deleted) {
    }

}
