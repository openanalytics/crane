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

import java.util.HashMap;
import java.util.List;

public class ControllerTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;
    private static CraneInstance redisInst;

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        inst = new CraneInstance("application-test-api.yml");
        redisInst = new CraneInstance("application-test-api.yml", 7071, new HashMap<>(), true);
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
        redisInst.close();
    }

    @Test
    public void testWithoutAuth() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @Test
    public void testWithAuth() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @Test
    public void testCacheHeaders() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/private_repo";
        String file = repository + "/file.txt";

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertHasNoCachingHeader();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertHasNoCachingHeader();
    }

    @Test
    public void testLogout() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");


        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @Test
    public void testRedis() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(redisInst);

        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/private_repo"));
        resp.assertSuccess();
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @Test
    public void indexPage() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);

        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        String body = resp.body();
        List<String> repositories = List.of("cache_txt_repo", "public_repo", "cache_txt_and_csv_repo", "mime_types");
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories = List.of(
                "public_repo", "cache_txt_and_csv_repo", "cache_txt_repo", "mime_types",
                "restricted_expression_using_and_repo", "restricted_simple_expression_repo",
                "restricted_to_groups_repo", "restricted_to_group_repo", "restricted_repo",
                "private_repo", "restricted_to_users_repo", "restricted_repo"
        );
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories = List.of(
                "private_repo", "public_repo", "cache_txt_and_csv_repo", "cache_txt_repo", "mime_types",
                "restricted_simple_expression_repo", "restricted_to_groups_repo", "restricted_to_users_repo"
        );
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));
    }
}
