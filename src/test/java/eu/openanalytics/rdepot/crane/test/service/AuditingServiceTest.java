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
package eu.openanalytics.rdepot.crane.test.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.openanalytics.rdepot.crane.security.auditing.FileAuditEventRepository;
import eu.openanalytics.rdepot.crane.test.api.RepositoryHostingHandlerTest;
import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sts.StsClient;

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
    private static final Logger logger = LoggerFactory.getLogger(RepositoryHostingHandlerTest.class);
    private static BufferedReader bufferedReader;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    static List<CraneInstance> instances = new ArrayList<>();

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
        try (StsClient client = StsClient.create()) {
            client.getCallerIdentity();
            instances.add(new CraneInstance("application-test-api-with-s3.yml", 7275, properties, true));
        } catch (SdkClientException ex) {
            logger.warn("No AWS credentials - skipping s3 tests");
        }
    }

    static List<CraneInstance> instances() {
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

    private FileAuditEventRepository.AuditEventData readAuditEventData() throws IOException, InterruptedException {
        Thread.sleep(50);
        String line = bufferedReader.readLine();
        return objectMapper.readValue(line, FileAuditEventRepository.AuditEventData.class);
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

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        checkUnauthenticatedAuditLog("/logout", "LOGOUT");

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/logout"));
        checkTwoAuditLogs(
                "/logout", "LOGOUT", ANONYMOUS_USER,
                "/logout-success", "AUTHENTICATION_SUCCESS", "demo"
        );

        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest("/logout"));
        checkTwoAuditLogs("/logout", "LOGOUT", ANONYMOUS_USER,
                "/logout-success", "AUTHENTICATION_SUCCESS", "test"
        );
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAuditingErrorHandlerEventPage(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/undefined_repository"));
        checkTwoAuditLogs(
                "/undefined_repository", "AUTHENTICATION_SUCCESS", "demo",
                "/undefined_repository", "AUTHORIZATION_FAILURE", "demo"
        );
    }

    private void checkAuditLog(String path, String type, String username) throws IOException, InterruptedException {
        FileAuditEventRepository.AuditEventData auditEventData = readAuditEventData();

        Assertions.assertEquals(username, auditEventData.getPrincipal());
        Assertions.assertEquals(path, auditEventData.getData().get("request_path"));
        Assertions.assertEquals(type, auditEventData.getType());
    }

    private void checkTwoAuditLogs(String path1, String type1, String username1, String path2, String type2, String username2) throws IOException, InterruptedException {
        // Reading two lines to prevent next tests from failing in case a previous tests fails
        // at a request logging two audit events
        FileAuditEventRepository.AuditEventData firstAuditEvent = readAuditEventData();
        FileAuditEventRepository.AuditEventData secondAuditEvent = readAuditEventData();

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
