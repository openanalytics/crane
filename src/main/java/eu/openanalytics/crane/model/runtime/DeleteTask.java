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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteTask {

    private final DeletionService deletionService;
    private final CranePath cranePath;
    private final DeletionService.StateEntry state;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DeleteTask(DeletionService deletionService, CranePath cranePath, DeletionService.StateEntry state) {
        this.deletionService = deletionService;
        this.cranePath = cranePath;
        this.state = state;
    }

    public void startDeletion() {
        try {
            // delete files in parallel
            state.files().parallelStream().forEach((file) -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                state.deleted().incrementAndGet();
            });

            // delete directories sequentially, otherwise directories might no be empty
            state.directories().forEach((file) -> {
                try {
                    try (final DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
                        if (stream.iterator().hasNext()) {
                            // ignore non-empty directories
                            // new files could have been written or it contains a directory the user has no access to
                            state.deleted().incrementAndGet();
                            return;
                        }
                    }
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                state.deleted().incrementAndGet();
            });
        } catch (Exception e) {
            logger.warn("Deletion of {} failed", cranePath, e);
        } finally {
            deletionService.removeFromState(cranePath);
        }
    }

}
