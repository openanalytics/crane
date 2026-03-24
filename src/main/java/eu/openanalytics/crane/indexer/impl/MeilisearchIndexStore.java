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
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import eu.openanalytics.crane.indexer.IIndexStore;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MeilisearchIndexStore implements IIndexStore {

    private final Client client;
    private final Index index;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public MeilisearchIndexStore() {
        client = new Client(new Config(
            "http://172.17.0.2:7700" // TODO
        ));
        index = client.index("files");
        index.updateFilterableAttributesSettings(new String[]{"extractedMetadata"});
    }

    @Override
    public void addOrUpdateSingleDocument(ExtractedDocument extractedDocument) throws JsonProcessingException {
        logger.debug("Storing extracted document with id {}", extractedDocument.id());
        TaskInfo task = index.addDocuments(objectMapper.writeValueAsString(extractedDocument));
        client.waitForTask(task.getTaskUid());
        logger.debug("Stored extracted document with id {}", extractedDocument.id());
    }

}
