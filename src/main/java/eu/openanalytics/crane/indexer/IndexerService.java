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
package eu.openanalytics.crane.indexer;

import com.meilisearch.sdk.Client;
import eu.openanalytics.crane.config.CraneConfig;
import eu.openanalytics.crane.config.IndexerConfig;
import eu.openanalytics.crane.event.FileDeletedEvent;
import eu.openanalytics.crane.event.FileUploadedEvent;
import eu.openanalytics.crane.event.FilesDeletedEvent;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.util.ExecutorServiceFactory;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@ConditionalOnBean(Client.class)
public class IndexerService {

    private final ITextExtractor textExtractor;
    private final IIndexStore indexStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ExecutorService repoExecutorService;
    private final ExecutorService fileExecutorService;
    private final IndexerConfig indexerConfig;
    private final CraneConfig craneConfig;

    public IndexerService(ITextExtractor textExtractor,
                          IIndexStore indexStore,
                          CraneConfig craneConfig,
                          IndexerConfig indexerConfig) {
        this.textExtractor = textExtractor;
        this.indexStore = indexStore;
        this.indexerConfig = indexerConfig;
        this.craneConfig = craneConfig;
        this.repoExecutorService = ExecutorServiceFactory.createWithLinkedBlockingQueue("indexer-repo", indexerConfig.getWorkers(), indexerConfig.getWorkers());
        this.fileExecutorService = ExecutorServiceFactory.createWithLinkedBlockingQueue("indexer-file", indexerConfig.getWorkers(), indexerConfig.getWorkers());
    }

    @PostConstruct
    public void init() {
        if (indexerConfig.getIndexOnStartup()) {
            Instant start = Instant.now();
            indexAllRepositories().join();
            Instant finish = Instant.now();
            logger.info("All repositories indexed in {} ", Duration.between(start, finish));
        }
    }

    public void indexSingleFile(CranePath cranePath) {
        ExtractedDocument extract = textExtractor.extract(cranePath);
        if (extract == null) {
            logger.debug("Skipping file {} for indexing", cranePath.getWebUri());
            return;
        }
        indexStore.addOrUpdateSingleDocument(extract);
    }

    public void deleteSingleFile(CranePath cranePath) {
        indexStore.deleteByid(List.of(cranePath.getId()));
    }

    public void deleteMultipleFiles(List<CranePath> cranePaths) {
        indexStore.deleteByid(cranePaths.stream().map(CranePath::getId).toList());
    }

    public CompletableFuture<Void> indexAllRepositories() {
        return CompletableFuture.allOf(
            craneConfig.getRepositories()
                .stream()
                .map(this::indexRepository)
                .toArray(CompletableFuture[]::new)
        );
    }

    public CompletableFuture<Void> indexRepository(Repository repository) {
        return indexDirectory(repository.getPath(""));
    }

    public CompletableFuture<Void> indexDirectory(CranePath cranePath) {
        // perform walk-tree in separate executor from file handling, otherwise they could lock each other
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Indexing Path '{}' indexed", cranePath.getWebUri());
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                cranePath.walkFileTree(new FileVisitor<>() {
                    @Override
                    public @NotNull FileVisitResult preVisitDirectory(CranePath dir, @NotNull BasicFileAttributes attrs) {
                        if (dir.isMetadataDirectory()) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFile(CranePath file, @NotNull BasicFileAttributes attrs) {
                        try {
                            futures.add(CompletableFuture.runAsync(() -> indexSingleFile(file), fileExecutorService));
                        } catch (Exception e) {
                            logger.warn("Exception during indexing of file '{}'", file.getWebUri(), e);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFileFailed(CranePath file, @NotNull IOException e) {
                        logger.warn("Visit failed during indexing of file '{}'", file.getWebUri(), e);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult postVisitDirectory(CranePath dir, @Nullable IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                logger.info("Path '{}' indexed, starting to delete missing files from index", cranePath.getWebUri());
                deleteMissingFilesForDirectory(cranePath);
                logger.info("Deleted missing files from '{}' (if any)", cranePath.getWebUri());
            } catch (IOException e) {
                logger.warn("Exception during indexing of directory '{}'", cranePath.getWebUri(), e);
                throw new RuntimeException(e);
            }
        }, repoExecutorService);
    }

    public void deleteMissingFilesForDirectory(CranePath cranePath) {
        List<String> toDelete = new ArrayList<>();

        indexStore.listAllSubPaths(cranePath).forEach((documentId) -> {
            CranePath path = new CranePath(cranePath.getRepository(), documentId.pathRelativeToRepository());
            if (!path.isExists()) {
                toDelete.add(documentId.id());
            }
        });

        if (!toDelete.isEmpty()) {
            logger.info("Found {} missing documents in '{}', deleting from index", toDelete.size(), cranePath.getWebUri());
            indexStore.deleteByid(toDelete);
        }
    }

    @EventListener(FileUploadedEvent.class)
    public void onFileUploadedEvent(FileUploadedEvent event) {
        try {
            logger.debug("Indexing '{}' after upload", event.getCranePath().getWebUri());
            CompletableFuture.runAsync(() -> indexSingleFile(event.getCranePath()), fileExecutorService);
        } catch (Exception e) {
            logger.warn("Failed to handle FileUploadedEvent", e);
        }
    }

    @EventListener(FileDeletedEvent.class)
    public void onFileDeletedEvent(FileDeletedEvent event) {
        try {
            logger.debug("Removing '{}' from index after delete", event.getCranePath().getWebUri());
            CompletableFuture.runAsync(() -> deleteSingleFile(event.getCranePath()), fileExecutorService);
        } catch (Exception e) {
            logger.warn("Failed to handle FileUploadedEvent", e);
        }
    }

    @EventListener(FilesDeletedEvent.class)
    public void onFilesDeletedEvent(FilesDeletedEvent event) {
        try {
            logger.debug("Removing {} files from index after delete", event.getCranePaths().size());
            CompletableFuture.runAsync(() -> deleteMultipleFiles(event.getCranePaths()), fileExecutorService);
        } catch (Exception e) {
            logger.warn("Failed to handle FileUploadedEvent", e);
        }
    }

}
