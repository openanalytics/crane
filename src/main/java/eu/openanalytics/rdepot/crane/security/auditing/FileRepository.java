/**
 * Crane
 *
 * Copyright (C) 2021-2022 Open Analytics
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
package eu.openanalytics.rdepot.crane.security.auditing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.rdepot.crane.config.CraneConfig;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FileRepository implements AuditEventRepository {
    private final Path auditLogFileName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileRepository(CraneConfig craneConfig) {
        this.auditLogFileName = craneConfig.getAuditLoggingPath();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void add(AuditEvent event) {
        if (auditLogFileName != null) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(auditLogFileName.toFile(), true));
                writer.write(this.objectMapper.writeValueAsString(event));
                writer.newLine();
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private static class AuditEventData extends AuditEvent{
        public AuditEventData() {
            super("", "", new HashMap<>());
        }

        public AuditEvent auditEvent() {
            return new AuditEvent(super.getTimestamp(), super.getPrincipal(), super.getType(), super.getData());
        }
    }
    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        if (!auditLogFileName.toFile().exists()) {
            return new ArrayList<>();
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(auditLogFileName.toFile()));
            return reader.lines().map((eventString) -> {
                try {
                    return objectMapper.readValue(eventString, AuditEventData.class).auditEvent();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }).filter(auditEvent -> {
                boolean condition = true;
                if (principal != null) {
                    condition = auditEvent.getPrincipal().equals(principal);
                }
                if (after != null) {
                    condition = condition && auditEvent.getTimestamp().isAfter(after);
                }
                if (type != null) {
                    condition = condition && auditEvent.getType().equals(type);
                }
                return condition;
            }).toList();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
