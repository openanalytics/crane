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
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.indexer.IIndexStore;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.model.runtime.CranePath;
import eu.openanalytics.crane.util.Retrying;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@ConditionalOnBean(Client.class)
public class MeilisearchIndexStore implements IIndexStore {

    private final Index index;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MeiliAddDocumentQueue meiliAddDocumentQueue;
    private final MeiliDeleteDocumentQueue meiliDeleteDocumentQueue;
    private final MetadataConvertorService metadataConvertorService;

    public MeilisearchIndexStore(SearchConfig searchConfig, Client client, MetadataConvertorService metadataConvertorService) {
        index = client.index(searchConfig.getIndexUid());
        index.updateFilterableAttributesSettings(new String[]{"extractedMetadata", "repositoryName", "webUri", "metadata"});
        meiliAddDocumentQueue = new MeiliAddDocumentQueue(index);
        meiliDeleteDocumentQueue = new MeiliDeleteDocumentQueue(index);
        this.metadataConvertorService = metadataConvertorService;
    }

    @Override
    public void addOrUpdateDocuments(List<ExtractedDocument> documents) {
        logger.debug("Queueing {} extracted documents for path {}", documents.size(), documents.get(0).webUri());
        meiliAddDocumentQueue.add(documents);
    }

    @Override
    public Stream<String> listAllSubPaths(CranePath path) {
        Iterator<String> iterator = new Iterator<>() {

            private static final Integer BATCH_SIZE = 100;
            private int offset = 0;
            private Iterator<String> currentBatch = Collections.emptyIterator();
            private boolean finished = false;

            @Override
            public boolean hasNext() {
                fetchIfNeeded();
                return currentBatch.hasNext();
            }

            @Override
            public String next() {
                fetchIfNeeded();
                return currentBatch.next();
            }

            private void fetchIfNeeded() {
                if (finished || currentBatch.hasNext()) {
                    return;
                }

                try {
                    SearchRequest query = SearchRequest.builder()
                        .offset(offset)
                        .limit(BATCH_SIZE)
                        .distinct("webUri")
                        .attributesToRetrieve(new String[]{"id", "webUri"})
                        .filter(createFilters(path))
                        .build();

                    Searchable results = index.search(query);
                    int numResults = results.getHits().size();
                    if (numResults == 0) {
                        finished = true;
                    }
                    currentBatch = results.getHits().stream().map(d -> d.get("webUri").toString()).iterator();
                    offset += numResults;
                } catch (Exception e) {
                    logger.warn("Error while fetching documents", e);
                }
            }

            private String[] createFilters(CranePath path) {
                return List.of("repositoryName=" + path.getRepository().getName(), "webUri STARTS WITH \"" + path.getWebUri() + "\"").toArray(new String[0]);
            }

        };

        return Streams.stream(iterator);
    }

    @Override
    public void deleteByWebUri(List<String> webUris) {
        try {
            // find ids to delete synchronously, this is fast
            List<String> ids = findIdsToDelete(webUris);
            // delete documents asynchronously, this is slower
            // from this point new documents might be added using the given webUris
            // only the original documents will be removed, even if new documents are added
            if (!ids.isEmpty()) {
                meiliDeleteDocumentQueue.add(ids);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isModified(CranePath cranePath) {
        SearchRequest query = SearchRequest.builder()
            .offset(0)
            .limit(1)
            .attributesToRetrieve(new String[]{"id", "syncMark"})
            .filter(new String[]{String.format("webUri=\"%s\"", cranePath.getWebUri())})
            .build();

        Searchable results = index.search(query);
        if (results.getHits().isEmpty()) {
            return true;
        }
        Map<String, Object> document = results.getHits().get(0);
        String syncMark = document.getOrDefault("syncMark", "").toString();
        return syncMark.isBlank() || !syncMark.equals(metadataConvertorService.generateSyncMark(cranePath));
    }

    public static boolean waitForTask(Index index, TaskInfo task) {
        return Retrying.retry((c, m) -> {
            TaskStatus status = index.getTask(task.getTaskUid()).getStatus();
            if (status.equals(TaskStatus.ENQUEUED) || status.equals(TaskStatus.PROCESSING)) {
                return Retrying.FAILURE;
            }
            return Retrying.SUCCESS;
        }, 60000_000);
    }

    private List<String> findIdsToDelete(List<String> toDelete) throws JsonProcessingException {
        int offset = 0;
        List<String> ids = new ArrayList<>();

        while (true) {
            Searchable result = index.search(SearchRequest.builder()
                .filter(new String[]{"webUri IN " + objectMapper.writeValueAsString(toDelete)})
                .limit(100)
                .offset(offset)
                .attributesToRetrieve(new String[]{"id"})
                .build());

            int numResults = result.getHits().size();
            if (numResults == 0) {
                break;
            }
            result.getHits().forEach(h -> ids.add(h.get("id").toString()));
            offset += numResults;
        }
        return ids;
    }

}
