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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Testcontainers
public class UploadControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(UploadControllerTest.class);
    private static final List<CraneInstance> instances = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        instances.add(new CraneInstance("application-test-upload-api.yml"));
        CraneInstance.addInstanceWithAwsAccess(instances, "application-test-upload-api-with-s3.yml", 7275, logger);
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
    public void testUploadToPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/public_repo/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToPrivateRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/private_repo/testUpload_token_%s.txt";
        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToPrivateRepositoryWithRestrictedAccess(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/restricted_repo/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToNestedMultiUserRestrictedRepositories(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/public_repo/restricted_to_users_repo/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToPublicInPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/public_repo/public_in_public_repo/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToOnlyWritePublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/write_public_repo/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("unauthenticated");
        // Cannot upload to this repo since user cannot get csrf token
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path)).assertUnauthorizedRedirectToLogIn();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToInaccessiblePathsInUserRestrictedRepositories(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/restricted_to_users_repo/inaccessible_path/testUpload_%s.txt";

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadInvalidPath(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/invalid/path";
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertForbidden();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToRepositoryWithMissingDirectory(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String genericPath = "/public_repo/testUpload_%s_directory/testUpload_%s.txt";

        String path = genericPath.formatted("unauthenticated", "unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("test", "test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("demo", "demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        genericPath = "/public_repo/testUpload_%s_nested/directory/path/testUpload_%s.txt";
        path = genericPath.formatted("unauthenticated", "unauthenticated");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("test", "test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        path = genericPath.formatted("demo", "demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadNoFileParameter(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/public_in_public_repo/testUpload.txt";
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n", "NotFile", "file.txt"));
        response.assertBadRequest();
        Assertions.assertTrue(response.body().contains("\"message\":"), response.body());
        Assertions.assertTrue(response.body().contains("\"status\":"), response.body());
        Assertions.assertTrue(response.body().contains("\"fail\""), response.body());
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testReplace(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/testUpload.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing upload\n")).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        // upload new file -> not allowed
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, "testing replace upload\n")).assertBadRequest();
        // file should not have been modified
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        // upload new file with replace=false -> not allowed
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path + "?replace=false", "testing replace upload\n")).assertBadRequest();
        // file should not have been modified
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing upload\n", response.body());

        // upload new file with replace=true -> allowed
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path + "?replace=true", "testing replace upload\n")).assertSuccess();
        response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals("testing replace upload\n", response.body());
    }

}
