/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.test.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.openanalytics.crane.security.auditing.FileAuditEventRepository;
import eu.openanalytics.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Testcontainers
public class AuditingServiceTest {

    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static final String ANONYMOUS_USER = "anonymousUser";
    private static final File auditLogsFile = new File("/tmp/auditingLogs.txt");
    private static final Logger logger = LoggerFactory.getLogger(AuditingServiceTest.class);
    private static BufferedReader bufferedReader;
    private static final List<FileAuditEventRepository.AuditEventData> events = new ArrayList<>();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    private static final List<CraneInstance> instances = new ArrayList<>();

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
        instances.add(new CraneInstance("application-test-api.yml", properties));
        CraneInstance.addInstanceWithAwsAccess(instances, "application-test-api-with-s3.yml", 7275, logger, properties);
    }

    private static List<CraneInstance> instances() {
        return instances;
    }

    @AfterAll
    public static void afterAll() {
        instances.forEach(CraneInstance::close);
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

    @ParameterizedTest
    @MethodSource("instances")
    public void testAuditingEventIndexPage(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        checkUnauthenticatedAuditLog("/", "LIST_REPOSITORIES");

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/"));
        checkTwoAuditLogs(
                "/", "AUTHENTICATION_SUCCESS", "demo",
                "/", "LIST_REPOSITORIES", "demo");

        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        checkTwoAuditLogs(
                "/", "AUTHENTICATION_SUCCESS", "test",
                "/", "LIST_REPOSITORIES", "test"
        );
    }

    private static void readAuditEventData() throws InterruptedException, IOException {
        Thread.sleep(50);
        String line = bufferedReader.readLine();
        while (line != null) {
            events.add(objectMapper.readValue(line, FileAuditEventRepository.AuditEventData.class));
            line = bufferedReader.readLine();
        }
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAuditingUnauthorizedEventPage(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/private_repo"));
        checkUnauthenticatedAuditLog("/private_repo", "AUTHORIZATION_FAILURE");

        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest("/restricted_repo"));
        checkTwoAuditLogs(
                "/restricted_repo", "AUTHENTICATION_SUCCESS", "test",
                "/restricted_repo", "AUTHORIZATION_FAILURE", "test"
        );
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAuditingLogoutEventPage(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        apiTestHelper.performLogoutRoutine(apiTestHelper::callWithoutAuth);
        checkUnauthenticatedAuditLog("/logout", "LOGOUT");

        apiTestHelper.performLogoutRoutine(apiTestHelper::callWithTokenAuthDemoUser);
        checkTwoAuditLogs(
                "/logout", "LOGOUT", ANONYMOUS_USER,
                "/logout-success", "AUTHENTICATION_SUCCESS", "demo"
        );

        apiTestHelper.performLogoutRoutine(apiTestHelper::callWithTokenAuthTestUser);

        checkTwoAuditLogs("/logout", "LOGOUT", ANONYMOUS_USER,
                "/logout-success", "AUTHENTICATION_SUCCESS", "test"
        );
    }

    private void checkAuditLog(String path, String type, String username) throws IOException, InterruptedException {
        readAuditEventData();
        FileAuditEventRepository.AuditEventData auditEventData = events.get(events.size()-1);

        Assertions.assertEquals(username, auditEventData.getPrincipal());
        Assertions.assertEquals(path, auditEventData.getData().get("request_path"));
        Assertions.assertEquals(type, auditEventData.getType());
    }

    private void checkTwoAuditLogs(String path1, String type1, String username1, String path2, String type2, String username2) throws IOException, InterruptedException {
        readAuditEventData();
        FileAuditEventRepository.AuditEventData firstAuditEvent = events.get(events.size() - 2);
        FileAuditEventRepository.AuditEventData secondAuditEvent = events.get(events.size() - 1);

        Assertions.assertEquals(username1, firstAuditEvent.getPrincipal());
        Assertions.assertEquals(path1, firstAuditEvent.getData().get("request_path"));
        Assertions.assertEquals(type1, firstAuditEvent.getType());

        Assertions.assertEquals(username2, secondAuditEvent.getPrincipal());
        Assertions.assertEquals(path2, secondAuditEvent.getData().get("request_path"));
        Assertions.assertEquals(type2, secondAuditEvent.getType());
    }

    private void checkUnauthenticatedAuditLog(String path, String type) throws IOException, InterruptedException {
        checkAuditLog(path, type, ANONYMOUS_USER);
    }

}
