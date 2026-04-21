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
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *  A queue that collects documents which need to be deleted and sent these in batches to Meilisearch.
 *
 *  This only works for deleting documents by id.
 */
public class MeiliAddDocumentQueue {

    private final static Integer MAX_BATCH_SIZE_BYTES = 75 * 1024 * 1024;
    private final static Integer MINIMUM_BATCH_SIZE = 100;
    private final static Integer MAX_TIME_BETWEEN_SYNC = 1;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LinkedBlockingQueue<ExtractedDocument> queue = new LinkedBlockingQueue<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Index index;
    private final Thread thread;

    public MeiliAddDocumentQueue(Index index) {
        this.index = index;
        thread = new Thread(this::run, "Meili-add-queue");
        thread.start();
    }

    public void run() {
        Instant lastSend = Instant.now();
        while (true) {
            try {
                if (!queue.isEmpty() && (queue.size() >= MINIMUM_BATCH_SIZE || Duration.between(lastSend, Instant.now()).toMinutes() > MAX_TIME_BETWEEN_SYNC)) {
                    sendBatch();
                } else {
                    Thread.sleep(2_00);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Error in queue thread", e);
            }
        }
    }

    private void sendBatch() throws InterruptedException, JsonProcessingException {
        List<ExtractedDocument> batch = new ArrayList<>();
        int batchSizeBytes = 0;
        // collect a batch of at most MAX_BATCH_SIZE_BYTES
        while (!queue.isEmpty() && batchSizeBytes < MAX_BATCH_SIZE_BYTES) {
            ExtractedDocument document = queue.take();
            batch.add(document);
            batchSizeBytes += objectMapper.writeValueAsString(document).length();
        }
        logger.debug("Sending batch of {} extracted documents (queue: {})", batch.size(), queue.size());
        TaskInfo task = index.addDocuments(objectMapper.writeValueAsString(batch));
        if (MeilisearchIndexStore.waitForTask(index, task)) {
            logger.debug("Stored batch of {} documents", batch.size());
        } else {
            logger.warn("Failed to store batch of {} documents", batch.size());
        }
    }

    public void add(List<ExtractedDocument> documents) {
        queue.addAll(documents);
    }

}
