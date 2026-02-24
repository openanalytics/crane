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
package eu.openanalytics.crane.test.upload;

import eu.openanalytics.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class DeletionTest {

    private static final Logger logger = LoggerFactory.getLogger(DeletionTest.class);
    private static final List<CraneInstance> instances = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        instances.add(new CraneInstance("application-test-upload-api.yml"));
        CraneInstance.addInstanceWithAwsAccess(instances, "application-test-upload-api-with-s3.yml", 7275, logger);
    }

    @BeforeEach
    public void beforeEach() {
        instances.forEach(CraneInstance::copyResourcesToTestLocation);
    }

    private static List<CraneInstance> instances() {
        return instances;
    }

    @AfterAll
    public static void afterAll() {
        instances.forEach(CraneInstance::close);
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testDeleteSingleFile(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/public_in_public_repo/file.txt";

        // file exists
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();

        // delete file
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("{\"status\":\"success\",\"data\":null}", response.body());

        // file no longer exists
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertNotFound();

        // delete file -> gives 404
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest(path));
        response.assertBadRequest();
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":{\"message\":\"File public_repo/public_in_public_repo/file.txt doesn't exist\"}}", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testDeleteComplexDirectory(CraneInstance instance) throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/";

        // directory file exists
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();

        // delete directory
        Awaitility.await().untilAsserted(() -> Assertions.assertEquals(
            "{\"status\":\"accepted\",\"data\":{\"total\":0,\"deleted\":0}}",
            apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest(path)).body())
        );

        // check that public file has been deleted
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/public_repo/public_in_public_repo/file.txt"));
        response.assertNotFound();

        // check other file has been deleted as well
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/public_repo/file.txt"));
        response.assertNotFound();

        // the repo contains directories that are not accessible by on-auth user, these shouldn't have be deleted
        response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/public_repo/private_repo/file.txt"));
        response.assertSuccess();

        response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/public_repo/restricted_repo/file.txt"));
        response.assertSuccess();

        response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/public_repo/restricted_to_users_repo/file.txt"));
        response.assertSuccess();
    }

}
