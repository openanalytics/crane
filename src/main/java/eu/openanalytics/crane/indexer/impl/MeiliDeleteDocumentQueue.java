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

import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *  A queue that collects documents which are sent in batches to Meilisearch.
 *
 *  Indexing in Meilisearch works better when sending many documents in big batches.
 *  Internally, Meilisearch creates a Task for each batch and adds it to a queue.
 *  This queue is only processed by a single thread in Meilisearch. Therefore, we need to
 *  keep the internal Meilisearch queue as small as possible, by creating big batches (i.e. big tasks).
 *  This class waits for such a big batch and the sends it at once.
 */
public class MeiliDeleteDocumentQueue {

    private final static Integer MAX_BATCH_SIZE = 1_000;
    private final static Integer MINIMUM_BATCH_SIZE = 100;
    private final static Integer MAX_TIME_BETWEEN_SYNC = 1;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Index index;
    private final Thread thread;

    public MeiliDeleteDocumentQueue(Index index) {
        this.index = index;
        thread = new Thread(this::run, "Meili-delete-queue");
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

    private void sendBatch() throws InterruptedException {
        List<String> batch = new ArrayList<>();
        while (!queue.isEmpty() && batch.size() < MAX_BATCH_SIZE) {
            String id = queue.take();
            batch.add(id);
        }
        logger.debug("Deleting batch of {} extracted documents (queue: {})", batch.size(), queue.size());
        TaskInfo task = index.deleteDocuments(batch);
        if (MeilisearchIndexStore.waitForTask(index, task)) {
            logger.debug("Deleted batch of {} documents", batch.size());
        } else {
            logger.warn("Failed to delete batch of {} documents", batch.size());
        }
    }

    public void add(List<String> ids) {
        queue.addAll(ids);
    }

}
