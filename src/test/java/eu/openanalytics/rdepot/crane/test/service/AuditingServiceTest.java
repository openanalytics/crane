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
package eu.openanalytics.rdepot.crane.test.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.openanalytics.rdepot.crane.security.auditing.FileAuditEventRepository;
import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
public class AuditingServiceTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;
    private static final File auditLogsFile = new File("/tmp/auditingLogs.txt");
    private static BufferedReader bufferedReader;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

    @BeforeAll
    public static void beforeAll() {
        clearAuditLoggingFile();
        try {
            bufferedReader = new BufferedReader(new FileReader(auditLogsFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        keycloakInstance.start();
        Map<String, String> properties = new HashMap<>();
        properties.put("app.audit-logging", auditLogsFile.getAbsolutePath());
        inst = new CraneInstance("application-test-api.yml", properties);
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testAuditingEventIndexPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        checkLoggingLine("/", "LIST_REPOSITORIES");

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/"));
        checkLoggingLine("/", "AUTHENTICATION_SUCCESS", "demo");
        checkLoggingLine("/", "LIST_REPOSITORIES", "demo");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        checkLoggingLine("/", "AUTHENTICATION_SUCCESS", "test");
        checkLoggingLine("/", "LIST_REPOSITORIES", "test");
    }

    private FileAuditEventRepository.AuditEventData readAuditEventData() throws IOException, InterruptedException {
        Thread.sleep(50);
        String line = bufferedReader.readLine();
        return objectMapper.readValue(line, FileAuditEventRepository.AuditEventData.class);
    }

    @Test
    public void testAuditingUnauthorizedEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/private_repo"));
        checkLoggingLine("/private_repo", "AUTHORIZATION_FAILURE");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/restricted_repo"));
        checkLoggingLine("/restricted_repo", "AUTHENTICATION_SUCCESS", "test");
        checkLoggingLine("/restricted_repo", "AUTHORIZATION_FAILURE", "test");
    }

    @Test
    public void testAuditingLogoutEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        checkLoggingLine("/logout", "LOGOUT");

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        checkLoggingLine("/logout", "LOGOUT");
        checkLoggingLine("/logout-success", "AUTHENTICATION_SUCCESS", "demo");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/logout"));
        checkLoggingLine( "/logout", "LOGOUT");
        checkLoggingLine("/logout-success", "AUTHENTICATION_SUCCESS", "test");
    }

    @Test
    public void testAuditingErrorHandlerEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/undefined_repository"));
        checkLoggingLine("/undefined_repository", "AUTHENTICATION_SUCCESS", "demo");
        checkLoggingLine("/undefined_repository", "AUTHORIZATION_FAILURE", "demo");
    }
    private void checkLoggingLine(String path, String type, String username) throws IOException, InterruptedException {
        FileAuditEventRepository.AuditEventData auditEventData = readAuditEventData();
        
        Assertions.assertEquals(auditEventData.getPrincipal(), username);
        Assertions.assertEquals(auditEventData.getData().get("path"), path);
        Assertions.assertEquals(auditEventData.getType(), type);
    }

    private void checkLoggingLine(String path, String type) throws IOException, InterruptedException {
        checkLoggingLine(path, type, "anonymousUser");
    }

    private static void clearAuditLoggingFile() {

        if (auditLogsFile.exists() && !auditLogsFile.delete()) {
            throw new RuntimeException("Could not delete auditing file!");
        }
        try {
            if (!auditLogsFile.createNewFile()) {
                throw new RuntimeException("Could not create auditing file!");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
