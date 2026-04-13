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
package eu.openanalytics.crane.indexer.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.DocumentsQuery;
import com.meilisearch.sdk.model.Results;
import com.meilisearch.sdk.model.TaskInfo;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.indexer.IIndexStore;
import eu.openanalytics.crane.indexer.model.DocumentId;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnBean(Client.class)
public class MeilisearchIndexStore implements IIndexStore {

    private final Client client;
    private final Index index;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public MeilisearchIndexStore(SearchConfig searchConfig, Client client) {
        this.client = client;
        index = client.index(searchConfig.getIndexUid());
        index.updateFilterableAttributesSettings(new String[]{"extractedMetadata", "repositoryName", "pathRelativeToRepository" });
    }

    @Override
    public void addOrUpdateSingleDocument(ExtractedDocument extractedDocument) {
        try {
            logger.debug("Storing extracted document with id {}", extractedDocument.id());
            TaskInfo task = index.addDocuments(objectMapper.writeValueAsString(extractedDocument));
            client.waitForTask(task.getTaskUid());
            logger.debug("Stored extracted document with id {}", extractedDocument.id());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Stream<DocumentId> listAllSubPaths(CranePath path) {
        Iterator<DocumentId> iterator = new Iterator<>() {

            private static final Integer BATCH_SIZE = 100;
            private int offset = 0;
            private Iterator<DocumentId> currentBatch = Collections.emptyIterator();
            private boolean finished = false;

            @Override
            public boolean hasNext() {
                fetchIfNeeded();
                return currentBatch.hasNext();
            }

            @Override
            public DocumentId next() {
                fetchIfNeeded();
                return currentBatch.next();
            }

            private void fetchIfNeeded() {
                if (finished || currentBatch.hasNext()) {
                    return;
                }

                try {
                    DocumentsQuery query = new DocumentsQuery();
                    query.setOffset(offset);
                    query.setLimit(BATCH_SIZE);
                    query.setFields(new String[]{"id", "pathRelativeToRepository"});
                    query.setFilter(createFilters(path));

                    Results<DocumentId> results = index.getDocuments(query, DocumentId.class);
                    int numResults = results.getResults().length;
                    if (offset + numResults >= results.getTotal()) {
                        finished = true;
                    }
                    currentBatch = Arrays.stream(results.getResults()).iterator();
                    offset += numResults;
                } catch (Exception e) {
                    logger.warn("Error while fetching documents", e);
                }
            }

            private String[] createFilters(CranePath path) {
                if (path.getPathRelativeToRepository().isBlank()) {
                    return List.of("repositoryName=" + path.getRepository().getName()).toArray(new String[0]);
                }
                return List.of("repositoryName=" + path.getRepository().getName(), "pathRelativeToRepository STARTS WITH" + path.getPathRelativeToRepository()).toArray(new String[0]);
            }

        };

        return Streams.stream(iterator);
    }

    @Override
    public void deleteByid(List<String> toDelete) {
        TaskInfo task = index.deleteDocuments(toDelete);
        client.waitForTask(task.getTaskUid());
        logger.debug("Deleted {} documents", toDelete.size());
    }

}
