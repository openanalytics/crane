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
package eu.openanalytics.crane.test.upload;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Testcontainers
public class UploadAuditingServiceTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static final String ANONYMOUS_USER = "anonymousUser";
    private static final File auditLogsFile = new File("/tmp/auditingLogs.txt");
    private static final Logger logger = LoggerFactory.getLogger(UploadAuditingServiceTest.class);
    private static BufferedReader bufferedReader;
    private final ObjectMapper objectMapper = new ObjectMapper()
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
        instances.add(new CraneInstance("application-test-upload-api.yml", properties));
        CraneInstance.addInstanceWithAwsAccess(instances, "application-test-upload-api-with-s3.yml", 7275, logger, properties);
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
    public void testAuditingEventUploadToPrivateRepo(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/private_repo/testUpload_private_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
        checkUnauthenticatedAuditLog(path, "AUTHORIZATION_FAILURE");

        path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
        checkTwoAuditLogs(
                path, "AUTHENTICATION_SUCCESS", "demo",
                path, "AUTHORIZATION_FAILURE", "demo"
        );

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
        checkTwoAuditLogs(
                path, "AUTHENTICATION_SUCCESS", "test",
                path, "AUTHORIZATION_FAILURE", "test"
        );
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAuditingEventUploadToPublicRepo(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/public_repo/testUpload_public_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        checkUnauthenticatedAuditLog(path, "UPLOAD");

        path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        checkTwoAuditLogs(
                path, "AUTHENTICATION_SUCCESS", "demo",
                path, "UPLOAD", "demo"
        );

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        checkTwoAuditLogs(
                path, "AUTHENTICATION_SUCCESS", "test",
                path, "UPLOAD", "test"
        );
    }
    private FileAuditEventRepository.AuditEventData readAuditEventData() throws IOException, InterruptedException {
        Thread.sleep(50);
        String line = bufferedReader.readLine();
        return objectMapper.readValue(line, FileAuditEventRepository.AuditEventData.class);
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
