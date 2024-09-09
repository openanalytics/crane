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

import com.google.common.io.Files;
import eu.openanalytics.crane.test.api.RepositoryHostingHandlerTest;
import eu.openanalytics.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Testcontainers
public class UploadHandlerTest {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryHostingHandlerTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    static List<CraneInstance> instances = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        instances.add(new CraneInstance("application-test-api.yml"));
        // TODO: add s3 instance to test s3 functionality once delete has been implemented
    }

    static List<CraneInstance> instances() {
        return instances;
    }

    @AfterAll
    public static void afterAll() {
        instances.forEach(CraneInstance::close);
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/testUpload.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToNestedPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/public_in_public_repo/testUpload.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }
}
