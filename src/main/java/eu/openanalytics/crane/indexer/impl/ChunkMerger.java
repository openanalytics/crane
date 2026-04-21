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

import eu.openanalytics.crane.indexer.model.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges a list of chunks into a list of bigger chunks, for faster indexing and better search.
 *
 * Only non-header chunks are merged and only consecutive chunks of the same type are merged.
 * The maximum size of merged chunks is 2000 bytes.
 */
public class ChunkMerger {

    private static final Integer MAX_CHUNK_BYTES = 2_000;

    public List<Chunk> merge(List<Chunk> chunks) {
        List<Chunk> result = new ArrayList<>();

        Chunk mergedChunk = null;

        for (Chunk chunk : chunks) {
            if (List.of("p", "ul", "ol").contains(chunk.type())) {
                if (mergedChunk == null) {
                    mergedChunk = chunk;
                } else if (mergedChunk.type().equals(chunk.type()) && mergedChunk.text().length() <= MAX_CHUNK_BYTES) {
                    mergedChunk = new Chunk( mergedChunk.text() +  "\n" + chunk.text(), mergedChunk.type());
                } else {
                    result.add(mergedChunk);
                    mergedChunk = chunk;
                }
            } else {
                if (mergedChunk != null) {
                    result.add(mergedChunk);
                    mergedChunk = null;
                }
                result.add(chunk);
            }
        }
        if (mergedChunk != null) {
            result.add(mergedChunk);
        }

        return result;
    }

}
