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
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

@Service
@ConditionalOnBean(Client.class)
public class IndexerService {

    private final ITextExtractor textExtractor;
    private final IIndexStore indexStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public IndexerService(ITextExtractor textExtractor, IIndexStore indexStore, CraneConfig craneConfig) throws Exception {
        this.textExtractor = textExtractor;
        this.indexStore = indexStore;

//        for (Repository repository : craneConfig.getRepositories()) {
//            indexDirectory(repository.getPath(""));
//        }
    }

    public void indexSingleFile(CranePath cranePath) throws Exception {
        ExtractedDocument extract = textExtractor.extract(cranePath);
        if (extract == null) {
            logger.debug("Skipping file {} for indexing", cranePath.getWebUri());
            return;
        }
        indexStore.addOrUpdateSingleDocument(extract);
    }

    public void removeSingleFile(CranePath cranePath) {

    }

    public void indexDirectory(CranePath cranePath) throws IOException {
        // TODO remove index documents for deleted files
        cranePath.walkFileTree(new FileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(CranePath dir, @NotNull BasicFileAttributes attrs) throws IOException {
                if (dir.isMetadataDirectory()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(CranePath file, @NotNull BasicFileAttributes attrs) throws IOException {
                try {
                    indexSingleFile(file);
                } catch (Exception e) {
                    // TODO
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFileFailed(CranePath file, @NotNull IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(CranePath dir, @Nullable IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
