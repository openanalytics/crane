package eu.openanalytics.rdepot.crane.test.api;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

public class ControllerTest {
    private static final CraneInstance inst = new CraneInstance("application-test-api.yml");
    private static final CraneInstance redisInst = new CraneInstance("application-test-redis.yml", 7071, new HashMap<>(), false);

    @AfterAll
    public static void afterAll() {
        inst.close();
        redisInst.close();
    }

    @Test
    public void testWithoutAuth() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
        String repository = "/";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @Test
    public void testWithAuth() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
        String repository = "/";
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
    }

    @Test
    public void testCacheHeaders() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
        String repository = "/private_repo";
        String file = repository + "/file.txt";

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertHasNoCachingHeader();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertHasNoCachingHeader();
    }

    @Test
    public void testLogout() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");


        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @Test
    public void testRedis() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(redisInst);

        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/private_repo"));
        resp.assertSuccess();
        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp.assertRedirectedTo("/logout-success");
    }

    @Test
    public void indexPage() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

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
