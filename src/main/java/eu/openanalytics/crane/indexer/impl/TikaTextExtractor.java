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
import eu.openanalytics.crane.indexer.model.ExtractedDocument;
import eu.openanalytics.crane.indexer.model.ExtractedMetadata;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.exception.TikaException;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
@ConditionalOnBean(Client.class)
public class TikaTextExtractor implements ITextExtractor {

    private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(MediaType.APPLICATION_PDF, MediaType.TEXT_HTML);
    private final MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public List<ExtractedDocument> extract(CranePath cranePath) {
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
            ExtractedMetadata extractedMetadata = extractMetadata(metadata, cranePath);
            List<Element> chunks = extractUsingJsoup(xhtml);

            List<ExtractedDocument> result = new ArrayList<>();
            for (Element chunk: chunks) {
                result.add(new ExtractedDocument(
                    UUID.randomUUID().toString(),
                    cranePath.getRepository().getName(),
                    cranePath.getWebUri(),
                    chunk.text(),
                    chunk.tagName(),
                    extractedMetadata
                ));
            }
            logger.debug("Extracted text and metadata from {}", cranePath.getWebUri());
            return result;
        } catch (IOException | TikaException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private Pair<String, Metadata> parseUsingTika(CranePath cranePath) throws IOException, TikaException, SAXException {
        ContentHandler handler = new ToXMLContentHandler();
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        parser.parse(cranePath.asInputStream(), handler, metadata);
        return Pair.of(handler.toString(), metadata);
    }

    private ExtractedMetadata extractMetadata(Metadata metadata, CranePath cranePath) throws MimeTypeException {
        return new ExtractedMetadata(
            getTitle(metadata, cranePath),
            cranePath.getMediaType().toString(),
            mimeTypes.forName(cranePath.getMediaType().toString()).getAcronym()
        );
    }

    private List<Element> extractUsingJsoup(String body) {
        Document doc = Jsoup.parse(body);
        Elements elements = doc.select("h1, h2, h3, h4, h5, h6, p, ul, ol, table");
        List<Element> chunks = new ArrayList<>();

        boolean sawHeader = elements.stream().noneMatch(e -> e.tagName().equals("h1"));
        for (Element element : elements) {
            if (!sawHeader && element.tagName().equals("h1")) {
                sawHeader = true;
            }
            if (sawHeader) {
                String text = element.text();
                if (!text.isBlank()) {
                    chunks.add(element);
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

    private String getMetadataField(Metadata metadata, List<String> candidates) {
        for (String candidate : candidates) {
            String value = metadata.get(candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
