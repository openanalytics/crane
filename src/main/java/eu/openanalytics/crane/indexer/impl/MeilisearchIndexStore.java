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
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import eu.openanalytics.crane.config.SearchConfig;
import eu.openanalytics.crane.indexer.IIndexStore;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

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
        index.updateFilterableAttributesSettings(new String[]{"extractedMetadata", "repositoryName"});
    }

    @Override
    public void addOrUpdateSingleDocument(ExtractedDocument extractedDocument) throws JsonProcessingException {
        logger.debug("Storing extracted document with id {}", extractedDocument.id());
        TaskInfo task = index.addDocuments(objectMapper.writeValueAsString(extractedDocument));
        client.waitForTask(task.getTaskUid());
        logger.debug("Stored extracted document with id {}", extractedDocument.id());
    }

}
