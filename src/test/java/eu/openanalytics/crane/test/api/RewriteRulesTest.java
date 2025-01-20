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
package eu.openanalytics.crane.test.api;

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

import java.util.ArrayList;
import java.util.List;

@Testcontainers
public class RewriteRulesTest {
    private static final Logger logger = LoggerFactory.getLogger(RewriteRulesTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    static List<CraneInstance> instances = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        instances.add(new CraneInstance("application-test-api-rewrite-rules.yml"));
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
    public void testRewritePublicRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/public_repo/rewrite1/param1/some-path/file.txt";
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());

        String path2 = "/public_repo/rewrite1/param1/some-path/file-not-found.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testRewritePrivateRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/private_repo/rewrite1/param1/some-path/file.txt";
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());

        String path2 = "/private_repo/rewrite1/param1/some-path/file-not-found.txt";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testNestedRewrite(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/public_repo/nested1/some-path1/some-path2/file.txt";
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Correct file!\n", apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());

        String path2 = "/public_repo/nested1/some-path1/some-path2/file-not-found.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testRewriteToInvalidRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/invalid-rewrite1/some-path1/some-path2/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).assertUnauthorizedRedirectToLogIn();
        ;
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testRewriteToInvalidDirectory(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/invalid-rewrite2/some-path1/some-path2/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testRewritePublicToPrivate(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/public_repo/to-private/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).assertUnauthorizedRedirectToLogIn();
        Assertions.assertEquals("Private text file\n", apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());
        Assertions.assertEquals("Private text file\n", apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());

        String path2 = "/public_repo/to-private/file-not-found.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path2)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testRewritePublicToRestricted(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        String path1 = "/public_repo/to-restricted/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path1)).assertUnauthorizedRedirectToLogIn();
        Assertions.assertEquals("Restricted text file only visible to demo\n", apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path1)).body());
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path1)).assertNotFound();

        String path2 = "/public_repo/to-restricted/file-not-found.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path2)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path2)).assertNotFound();
    }


}
