/**
 * Crane
 *
 * Copyright (C) 2021-2024 Open Analytics
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
package eu.openanalytics.crane.security.auditing;

import eu.openanalytics.crane.config.CraneConfig;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class AuditingConfiguration {

    @Bean
    @ConditionalOnProperty(value = "app.audit-logging")
    public AuditEventRepository auditEventRepository(CraneConfig craneConfig) throws IOException {
        return new FileAuditEventRepository(craneConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditEventRepository noAuditEventRepository(CraneConfig craneConfig) {
        return new NoAuditEventRepository(craneConfig);
    }

}
