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
import eu.openanalytics.crane.indexer.ITextExtractor;
import eu.openanalytics.crane.indexer.model.Chunk;
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.indexer.model.ExtractedMetadata;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@ConditionalOnBean(Client.class)
public class TikaTextExtractor implements ITextExtractor {

    private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(MediaType.APPLICATION_PDF, MediaType.TEXT_HTML);
    private static final String AUTHORS_SPLIT_REGEX = "[;,]";
    private final MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChunkMerger chunkMerger = new ChunkMerger();
    private final LanguageDetector languageDetector = new OptimaizeLangDetector().loadModels();
    private final ObjectMapper metadataObjectMapper = new ObjectMapper(new YAMLFactory());
    private final MetadataConvertorService metadataConvertorService;

    public TikaTextExtractor(MetadataConvertorService metadataConvertorService) {
        this.metadataConvertorService = metadataConvertorService;
    }

    @Override
    public List<ExtractedDocument> extract(CranePath cranePath, CranePath metadataCranePath) {
        Pair<ExtractedMetadata, List<Chunk>> result = extractMetadataAndChunks(cranePath);
        if (result == null) {
            return null;
        }
        return processMetadataAndChunks(cranePath, metadataCranePath, result.getKey(), result.getValue());
    }

    @Override
    public ExtractedMetadata extractMetadata(CranePath cranePath) {
        Pair<ExtractedMetadata, List<Chunk>> result = extractMetadataAndChunks(cranePath);
        if (result == null) {
            return null;
        }
        return result.getKey();
    }

    private Pair<ExtractedMetadata, List<Chunk>> extractMetadataAndChunks(CranePath cranePath) {
        if (cranePath.isDirectory()) {
            throw new IllegalArgumentException("Path is a directory, can only extract text from files!");
        }
        if (cranePath.isMetadataFile()) {
            throw new IllegalArgumentException("Path is a metadata file and cannot be indexed!");
        }
        if (!cranePath.isExists()) {
            throw new IllegalArgumentException("File does not exist!");
        }
        if (!SUPPORTED_MEDIA_TYPES.contains(cranePath.getMediaType())) {
            return null;
        }
        logger.debug("Extracting text and metadata from {}", cranePath.getWebUri());

        try {
            Pair<String, Metadata> parseResult = parseUsingTika(cranePath);
            String xhtml = parseResult.getLeft();
            Metadata metadata = parseResult.getRight();
            List<Chunk> chunks = extractUsingJsoup(xhtml);
            ExtractedMetadata extractedMetadata = extractMetadata(metadata, cranePath, chunks);

            logger.debug("Extracted text and metadata from {}", cranePath.getWebUri());
            return Pair.of(extractedMetadata, chunks);
        } catch (IOException | TikaException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ExtractedDocument> processMetadataAndChunks(CranePath cranePath, CranePath metadataCranePath, ExtractedMetadata extractedMetadata, List<Chunk> chunks) {
        Map<String, Object> userMetadata = parseMetadata(metadataCranePath);
        Map<String, Object> mergedMetadata = metadataConvertorService.mergeMedata(extractedMetadata, userMetadata);
        chunks = chunkMerger.merge(chunks);

        List<ExtractedDocument> result = new ArrayList<>();
        for (Chunk chunk: chunks) {
            result.add(new ExtractedDocument(
                UUID.randomUUID().toString(),
                cranePath.getRepository().getName(),
                cranePath.getWebUri(),
                chunk.text(),
                chunk.type(),
                metadataConvertorService.generateSyncMark(cranePath),
                extractedMetadata,
                mergedMetadata
            ));
        }
        logger.debug("Processed text and metadata from {}", cranePath.getWebUri());
        return result;
    }

    private Pair<String, Metadata> parseUsingTika(CranePath cranePath) throws IOException, TikaException, SAXException {
        ContentHandler handler = new ToXMLContentHandler();
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        try (InputStream inputStream = cranePath.asInputStream()) {
            parser.parse(inputStream, handler, metadata);
            return Pair.of(handler.toString(), metadata);
        }
    }

    private ExtractedMetadata extractMetadata(Metadata metadata, CranePath cranePath, List<Chunk> chunks) throws MimeTypeException {
        ZonedDateTime date = getDate(metadata, cranePath);
        return new ExtractedMetadata(
            cranePath.getName(),
            getTitle(metadata, cranePath),
            cranePath.getMediaType().toString(),
            mimeTypes.forName(cranePath.getMediaType().toString()).getAcronym(),
            getLanguage(metadata, chunks),
            date.toString(),
            date.getYear(),
            date.getMonthValue(),
            date.getDayOfMonth(),
            getAuthors(metadata)
        );
    }

    private List<Chunk> extractUsingJsoup(String body) {
        Document doc = Jsoup.parse(body);
        Elements elements = doc.select("h1, h2, h3, h4, h5, h6, p, ul, ol, table");
        List<Chunk> chunks = new ArrayList<>();

        boolean sawHeader = elements.stream().noneMatch(e -> e.tagName().equals("h1"));
        for (Element element : elements) {
            if (!sawHeader && element.tagName().equals("h1")) {
                sawHeader = true;
            }
            if (sawHeader) {
                String text = element.text();
                if (!text.isBlank()) {
                    chunks.add(new Chunk(text, element.tagName()));
                }
            }
        }
        return chunks;
    }

    private String getTitle(Metadata metadata, CranePath cranePath) {
        // TODO use h1 as fallback title
        String documentTitle = getMetadataField(metadata, List.of("title", "dc:title", "og:title"));
        if (documentTitle != null && !documentTitle.isBlank() && !documentTitle.equals("untitled")) {
            return documentTitle;
        }
        // TODO do we want to remove the extension?
        return cranePath.getName();
    }

    private String getLanguage(Metadata metadata, List<Chunk> chunks) {
        // collect first 100 chunks for detecting language
        String text = chunks.stream()
            .limit(100)
            .map(Chunk::text)
            .collect(Collectors.joining("\n" ));

        LanguageResult result = languageDetector.detect(text);
        if (result.isReasonablyCertain()) {
            return result.getLanguage();
        }
        return null;
    }

    private ZonedDateTime getDate(Metadata metadata, CranePath cranePath) {
        String creationDate = getMetadataField(metadata, List.of("dcterms:modified", "pdf:docinfo:modified", "dcterms:created", "pdf:docinfo:created "));
        if (creationDate != null) {
            return ZonedDateTime.parse(creationDate).withZoneSameInstant(ZoneOffset.UTC);
        }
        // last modified is already in UTC
        return ZonedDateTime.ofInstant(cranePath.getLastModifiedTime(), ZoneOffset.UTC);
    }

    private List<String> getAuthors(Metadata metadata) {
        String[] authors = metadata.getValues("dc:creator");
        if (authors != null) {
            return Arrays.stream(authors).flatMap(a ->
                Arrays.stream(a.split(AUTHORS_SPLIT_REGEX)).map(String::trim)
            ).toList();
        }
        return List.of();
    }

    private String getMetadataField(Metadata metadata, List<String> candidates) {
        for (String candidate : candidates) {
            String value = metadata.get(candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> parseMetadata(CranePath cranePath) {
        if (!cranePath.isExists()) {
            return null;
        }
        try {
            try (InputStream inputStream = cranePath.asInputStream()) {
                return metadataConvertorService.convertMetadata(metadataObjectMapper.readValue(inputStream, Map.class));
            }
        } catch (IOException e) {
            logger.warn("Unable to parse metadata", e);
            return null;
        }
    }

}
