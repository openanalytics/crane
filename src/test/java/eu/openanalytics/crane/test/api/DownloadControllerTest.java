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
import eu.openanalytics.crane.test.helpers.NativeResponse;
import eu.openanalytics.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Testcontainers
public class DownloadControllerTest {
    private static final Logger logger = LoggerFactory.getLogger(DownloadControllerTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static final List<CraneInstance> instances = new ArrayList<>();
    private static final List<CraneInstance> instanceWithoutFirewall = new ArrayList<>();
    private static CraneInstance groupsInst;

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        instances.add(new CraneInstance("application-test-api.yml"));
        CraneInstance.addInstanceWithAwsAccess(instances, "application-test-api-with-s3.yml", 7275, logger);
        groupsInst = new CraneInstance("application-test-keycloak-groups.yml", 7273);

        instanceWithoutFirewall.add(new CraneInstance("application-test-api.yml", 7276, new HashMap<>(), true, true));
    }

    private static List<CraneInstance> instances() {
        return instances;
    }

    private static List<CraneInstance> instanceWithoutFirewall() {
        return instanceWithoutFirewall;
    }

    @AfterAll
    public static void afterAll() {
        instances.forEach(CraneInstance::close);
        instanceWithoutFirewall.forEach(CraneInstance::close);
        groupsInst.close();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToPublicRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/public_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();


        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToPrivateRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/private_repo";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToPrivateRepositoryWithRestrictedAccess(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToRepositoryRestrictedToMultipleUsers(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_to_users_repo";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupKeycloakRoles(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_to_group_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupsKeycloakRoles(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_to_groups_repo";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupKeycloakGroups() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(groupsInst);
        String repository = "/restricted_to_group_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupsKeycloakGroups() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(groupsInst);
        String repository = "/restricted_to_groups_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToSimpleExpressionRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_simple_expression_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToExpressionUsingAndRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/restricted_expression_using_and_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNonExistentRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/this-does-not-exist";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNonExistentFileOrRepositoryInRepositories(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String file = "/public_repo/undefined.file";
        String repository = "/public_repo/undefined/";
        String nestedRepository = "/public_repo/undefined/test";
        List<String> paths = Arrays.asList(file, repository, nestedRepository);
        for (String path : paths) {
            apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path)).assertNotFound();

            apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path)).assertNotFound();

            apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path)).assertNotFound();
        }
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNestedPublicRepositories(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/public_repo/public_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNestedPrivateRepositories(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String privateRepo = "/public_repo/private_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(privateRepo)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(privateRepo + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();

    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNestedRestrictedRepositories(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String restrictedRepo = "/public_repo/restricted_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedRepo)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedRepo)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedRepo)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo)).assertNotFound();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertNotFound();

    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testAccessToNestedMultiUserRestrictedRepositories(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String restrictedToMultipleUsers = "/public_repo/restricted_to_users_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testMimeTypes(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/mime_types";
        String text = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(text)).assertPlainSuccess();

        String html = repository + "/file.html";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(html)).assertHtmlSuccess();

        String csv = repository + "/file.csv";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(csv)).assertCsvSuccess();

        String json = repository + "/file.json";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(json)).assertJsonSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testSingleCacheRule(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/cache_txt_repo";

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository))
            .assertHasNoCachingHeader();

        String text = repository + "/file.txt";
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(text));
        resp.assertMissingNoCachingHeader();
        resp.assertMaxAgeInSeconds(24 * 3600);

        String html = repository + "/file.html";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(html));
        resp.assertHasNoCachingHeader();
        resp.assertMaxAgeInSeconds(0);

        String csv = repository + "/file.csv";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(csv));
        resp.assertHasNoCachingHeader();
        resp.assertMaxAgeInSeconds(0);

        String json = repository + "/file.json";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(json));
        resp.assertHasNoCachingHeader();
        resp.assertMaxAgeInSeconds(0);
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testMultipleCacheRules(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/cache_txt_and_csv_repo";

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository))
            .assertHasNoCachingHeader();

        String text = repository + "/file.txt";
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(text));
        resp.assertMissingNoCachingHeader();
        resp.assertMaxAgeInSeconds(60);

        String html = repository + "/file.html";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(html));
        resp.assertHasNoCachingHeader();
        resp.assertMaxAgeInSeconds(0);

        String csv = repository + "/file.csv";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(csv));
        resp.assertMissingNoCachingHeader();
        resp.assertMaxAgeInSeconds(50);

        String json = repository + "/file.json";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(json));
        resp.assertHasNoCachingHeader();
        resp.assertMaxAgeInSeconds(0);
    }

    @ParameterizedTest
    @MethodSource("instances")
    void testDefaultCache(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        Response resp = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/public_repo/default_cached_file.html"));
        resp.assertMissingNoCachingHeader();
        resp.assertMaxAgeInSeconds(3960);
    }

    @ParameterizedTest
    @MethodSource({"instances", "instanceWithoutFirewall"})
    void testPathTraversalAttack(CraneInstance instance) {
        // note this test uses the native JDK client, because the OkHttp client canonicalizes the URL and therefore removes the encoded and '..' characters.
        // see https://github.com/square/okhttp/issues/7089
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        List<String> attackPaths = List.of(
            "/%2e%2e%2f%2e%2e%2fetc/passwd",
            "/public_repo/%2e%2e%2f%2e%2e%2f%2e%2e%2fetc/passwd",
            "/%2e%2e/%2e%2e/etc/passwd",
            "/public_repo/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
            "/..%2f..%2fetc/passwd",
            "/public_repo/..%2f..%2f..%2f/etc/passwd",
            "/../../etc/password",
            "/../file.txt",
            "/public_repo/../file.txt",
            "/public_repo/%252E%252E%252Ffile.txt", // double encoded
            "/public_repo/my_file%25%25.txt" // encoded percent character
        );

        for (String path : attackPaths) {
            NativeResponse resp = apiTestHelper.nativeCallWithTokenAuthDemoUser(apiTestHelper.createNativeHtmlRequest(path));
            resp.assertBadRequest();

            resp = apiTestHelper.nativeCallWithoutAuth(apiTestHelper.createNativeHtmlRequest(path));
            resp.assertBadRequest();

            resp = apiTestHelper.nativeCallWithoutAuth(apiTestHelper.createNativeJsonRequest(path));
            resp.assertBadRequest();
        }

        List<String> allowedPaths = List.of("/some_repo/test+.txt"); // regular + character allowed
        for (String path : allowedPaths) {
            NativeResponse resp = apiTestHelper.nativeCallWithTokenAuthDemoUser(apiTestHelper.createNativeHtmlRequest(path));
            resp.assertNotFound();

            resp = apiTestHelper.nativeCallWithoutAuth(apiTestHelper.createNativeHtmlRequest(path));
            resp.assertUnauthorizedRedirectToLogIn();

            resp = apiTestHelper.nativeCallWithoutAuth(apiTestHelper.createNativeJsonRequest(path));
            resp.assertNotFound();
        }
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testPublicInPublicRepository(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/public_repo/public_in_public_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testOnErrorExpressionOption(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/on_error_expression";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();

        file = "/public_repo/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testOnErrorExpressionOptionReturnFalse(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/on_error_expression_return_false";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();

        file = "/public_repo/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testCustomRepositoryStorageLocation(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/custom_storage_location";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();


        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();

        file = "/in_custom_storage_location/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthDemoUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithOidcAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
    }
}
