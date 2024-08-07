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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Testcontainers
public class RepositoryHostingHandlerTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;
    private static CraneInstance groupsInst;

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        inst = new CraneInstance("application-test-api.yml");
        groupsInst = new CraneInstance("application-test-keycloak-groups.yml", 7273, new HashMap<>(), true);
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
        groupsInst.close();
    }

    @Test
    public void testAccessToPublicRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/public_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();


        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToPrivateRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/private_repo";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccess() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToRepositoryRestrictedToMultipleUsers() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_to_users_repo";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupKeycloakRoles() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_to_group_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupsKeycloakRoles() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_to_groups_repo";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupKeycloakGroups() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(groupsInst);
        String repository = "/restricted_to_group_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToPrivateRepositoryWithRestrictedAccessUsingGroupsKeycloakGroups() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(groupsInst);
        String repository = "/restricted_to_groups_repo";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToSimpleExpressionRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_simple_expression_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToExpressionUsingAndRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/restricted_expression_using_and_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToNonExistentRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/this-does-not-exist";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToNonExistentFileOrRepositoryInRepositories() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String file = "/public_repo/undefined.file";
        String repository = "/public_repo/undefined/";
        String nestedRepository = "/public_repo/undefined/test";
        List<String> paths = Arrays.asList(file, repository, nestedRepository);
        for (String path : paths) {
            apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path)).assertNotFound();

            apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(path)).assertNotFound();

            apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(path)).assertNotFound();
        }
    }

    @Test
    public void testAccessToNestedPublicRepositories() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/public_repo/public_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
    }

    @Test
    public void testAccessToNestedPrivateRepositories() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String privateRepo = "/public_repo/private_repo";
        String file = "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(privateRepo)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo)).assertSuccess();

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(privateRepo + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(privateRepo + file)).assertSuccess();

    }

    @Test
    public void testAccessToNestedRestrictedRepositories() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String restrictedRepo = "/public_repo/restricted_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedRepo)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(restrictedRepo)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo)).assertNotFound();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(restrictedRepo + file)).assertNotFound();

    }

    @Test
    public void testAccessToNestedMultiUserRestrictedRepositories() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String restrictedToMultipleUsers = "/public_repo/restricted_to_users_repo";
        String file = "/file.txt";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers)).assertSuccess();

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(restrictedToMultipleUsers + file)).assertSuccess();
    }

    @Test
    public void testMimeTypes() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
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

    @Test
    public void testSingleCacheRule() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/cache_txt_repo";

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository))
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

    @Test
    public void testMultipleCacheRules() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/cache_txt_and_csv_repo";

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository))
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

    @Test
    void testDefaultCache() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);

        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/public_repo/default_cached_file.html"));
        resp.assertMissingNoCachingHeader();
        resp.assertMaxAgeInSeconds(3960);
    }

    @Test
    void testPathTraversalAttack() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);

        String attack_path = "/%2e%2e%2f%2e%2e%2fetc/passwd";
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/%2e%2e%2f%2e%2e%2fetc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/public_repo/%2e%2e%2f%2e%2e%2f%2e%2e%2fetc/passwd";
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/public_repo/%2e%2e%2f%2e%2e%2f%2e%2e%2fetc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/%2e%2e/%2e%2e/etc/passwd";
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertNotFound();

        attack_path = "/%2e%2e/%2e%2e/etc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertUnauthorizedRedirectToLogIn();

        attack_path = "/public_repo/%2e%2e/%2e%2e/%2e%2e/etc/passwd";
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertNotFound();

        attack_path = "/public_repo/%2e%2e/%2e%2e/%2e%2e/etc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertUnauthorizedRedirectToLogIn();

        attack_path = "/..%2f..%2fetc/passwd";
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/..%2f..%2fetc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/public_repo/..%2f..%2f..%2f/etc/passwd";
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();

        attack_path = "/public_repo/..%2f..%2f..%2f/etc/passwd";
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(attack_path));
        resp.assertBadRequest();
    }

    @Test
    public void testPublicInPublicRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/public_repo/public_in_public_repo";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
    }

    @Test
    public void testOnErrorExpressionOption() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/on_error_expression";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();

        file = "/public_repo/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertRedirectedTo("/public_repo" + file);
    }

    @Test
    public void testOnErrorExpressionOptionReturnFalse() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(inst);
        String repository = "/on_error_expression_return_false";
        String file = "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertSuccess();

        file = "/public_repo/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository + file)).assertNotFound();
    }
}
