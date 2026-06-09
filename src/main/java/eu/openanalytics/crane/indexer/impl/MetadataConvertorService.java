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

import com.meilisearch.sdk.Client;
import eu.openanalytics.crane.indexer.model.ExtractedMetadata;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO move to own package or services
@Service
@ConditionalOnBean(Client.class)
public class MetadataConvertorService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @SuppressWarnings("unchecked")
    public Map<String, Object> convertMetadata(Map<?, ?> metadata) {
        Map<String, Object> result = new HashMap<>();
        if (metadata == null) {
            return result;
        }
        for (Map.Entry<?, ?> item : metadata.entrySet()) {
            if (item.getKey() == null || !(item.getKey() instanceof String key)) {
                logger.warn("Invalid metadata key type {}", item.getKey());
                continue;
            }
            if (item.getValue() == null || item.getValue() instanceof String || item.getValue() instanceof Number || item.getValue() instanceof Boolean) {
                result.put(key, item.getValue());
            } else if (item.getValue() instanceof List<?> list) {
                result.put(key, convertList(list));
            } else {
                result.put(key, null);
                logger.warn("Invalid metadata type with key {}, invalid type: {}", key, item.getValue().getClass());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked" )
    private List<Object> convertList(List<?> list) {
        if (list.stream().allMatch(i -> i instanceof String)) {
            return (List<Object>) list;
        }
        if (list.stream().allMatch(i -> i instanceof Map)) {
            return list.stream().map(m -> (Object) convertMetadata((Map<?, ?>) m)).toList();
        }
        logger.warn("Invalid metadata type in list");
        return null;
    }

    public Map<String, Object> mergeMedata(ExtractedMetadata extractedMetadata, Map<String, Object> userMetadata) {
        if (userMetadata == null) {
            userMetadata = new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>(userMetadata);

        if (!result.containsKey("title")) {
            result.put("title", extractedMetadata.title());
        }

        if (!result.containsKey("mediaType")) {
            result.put("mediaType", extractedMetadata.mediaType());
        }

        if (!result.containsKey("humanReadableMediaType")) {
            result.put("humanReadableMediaType", extractedMetadata.humanReadableMediaType());
        }

        if (!result.containsKey("language")) {
            result.put("language", extractedMetadata.language());
        }

        if (!result.containsKey("creationDate")) {
            result.put("creationDate", extractedMetadata.creationDate());
            if (!result.containsKey("year")) {
                result.put("year", extractedMetadata.year());
            }
            if (!result.containsKey("month")) {
                result.put("month", extractedMetadata.month());
            }
            if (!result.containsKey("day")) {
                result.put("day", extractedMetadata.day());
            }
        } else {
            ZonedDateTime creationDate = ZonedDateTime.parse(extractedMetadata.creationDate()).withZoneSameInstant(ZoneOffset.UTC);
            if (!result.containsKey("year")) {
                result.put("year", creationDate.getYear());
            }
            if (!result.containsKey("month")) {
                result.put("month", creationDate.getMonthValue());
            }
            if (!result.containsKey("day")) {
                result.put("day", creationDate.getDayOfMonth());
            }
        }

        if (!result.containsKey("authors")) {
            result.put("authors", extractedMetadata.authors());
        }

        return result;
    }

    public String generateSyncMark(CranePath cranePath) {
        StringBuilder syncMark = new StringBuilder();
        syncMark.append(cranePath.getLastModifiedTimeInMillis());
        syncMark.append("#");
        syncMark.append(cranePath.getSize());
        syncMark.append("#");
        if (cranePath.getMetadataFilePath().isExists()) {
            syncMark.append(cranePath.getMetadataFilePath().getLastModifiedTimeInMillis());
            syncMark.append("#");
            syncMark.append(cranePath.getMetadataFilePath().getSize());
        }
        return syncMark.toString();
    }

}
