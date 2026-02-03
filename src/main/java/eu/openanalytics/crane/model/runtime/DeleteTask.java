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
package eu.openanalytics.crane.model.runtime;

import eu.openanalytics.crane.service.DeletionService;
import eu.openanalytics.crane.upload.UploadAccessControlService;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DeleteTask extends SimpleFileVisitor<CranePath> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UploadAccessControlService uploadAccessControlService;
    private final DeletionService deletionService;
    private final CranePath cranePath;
    private final Authentication auth;

    public DeleteTask(DeletionService deletionService, UploadAccessControlService uploadAccessControlService, CranePath cranePath, Authentication auth) {
        this.deletionService = deletionService;
        this.uploadAccessControlService = uploadAccessControlService;
        this.cranePath = cranePath;
        this.auth = auth;
    }

    public void startDeletion() {
        try {
            cranePath.walkFileTree(this);
            deletionService.removeFromState(cranePath);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public @NotNull FileVisitResult preVisitDirectory(@NotNull CranePath dir, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(dir, auth)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult postVisitDirectory(@NotNull CranePath dir, IOException exception) {
        if (!cranePath.isExistsInConfig()) {
            delete(dir);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public @NotNull FileVisitResult visitFile(@NotNull CranePath file, @NotNull BasicFileAttributes attributes) {
        if (!uploadAccessControlService.canAccess(file, auth)) {
            return FileVisitResult.CONTINUE;
        }
        delete(file);
        return FileVisitResult.CONTINUE;
    }

    private void delete(@NotNull CranePath path) {
        if (path.getRepository().usesS3()) {
            throw new NotImplementedException("S3 directory deletion not implemented yet");
        } else {
            path.getStorageFile().delete();
        }
    }
}
