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
package eu.openanalytics.rdepot.crane.test.api;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.rdepot.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ControllerTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;
    private static CraneInstance s3Inst;
    private static CraneInstance redisInst;

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        inst = new CraneInstance("application-test-api.yml");
        s3Inst = new CraneInstance("application-test-api-with-s3.yml", 7275);
        redisInst = new CraneInstance("application-test-api.yml", 7071, Map.of("spring.session.store-type", "redis"), true);
    }

    static Stream<Arguments> instances() {
        return Stream.of(
                Arguments.of(inst),
                Arguments.of(s3Inst)
        );
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
        s3Inst.close();
        redisInst.close();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testWithoutAuth(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testWithAuth(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/";
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testCacheHeaders(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String repository = "/private_repo";
        String file = repository + "/file.txt";

        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(repository)).assertHasNoCachingHeader();
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(file)).assertHasNoCachingHeader();
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testLogout(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        Response resp = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");


        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @Test
    public void testRedis() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(redisInst);

        Response resp = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/private_repo"));
        resp.assertSuccess();
        resp = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void indexPage(CraneInstance instance) {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);

        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        String body = resp.body();
        List<String> repositories = List.of("cache_txt_repo", "public_repo", "cache_txt_and_csv_repo", "mime_types");
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories = List.of(
                "public_repo", "cache_txt_and_csv_repo", "cache_txt_repo", "mime_types",
                "restricted_expression_using_and_repo", "restricted_simple_expression_repo",
                "restricted_to_groups_repo", "restricted_to_group_repo", "restricted_repo",
                "private_repo", "restricted_to_users_repo", "restricted_repo"
        );
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories = List.of(
                "private_repo", "public_repo", "cache_txt_and_csv_repo", "cache_txt_repo", "mime_types",
                "restricted_simple_expression_repo", "restricted_to_groups_repo", "restricted_to_users_repo"
        );
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));
    }
}
