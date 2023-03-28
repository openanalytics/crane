package eu.openanalytics.rdepot.crane.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

public class CraneFile {

    private final String fileName;

    private final Instant lastModifiedTime;

    private final Long size;

    public CraneFile(String fileName, Instant lastModifiedTime, Long size) {
        this.fileName = fileName;
        this.lastModifiedTime = lastModifiedTime;
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public static CraneFile createFromPath(Path path) {
        try {
            BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new CraneFile(path.getFileName().toString(), basicFileAttributes.lastModifiedTime().toInstant(), basicFileAttributes.size());
        } catch (IOException e) {
            return null;
        }
    }

    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }

    public Long getSize() {
        return size;
    }
}
