package eu.openanalytics.rdepot.crane.test.api;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class ControllerTest {
    private static final CraneInstance inst = new CraneInstance("application-test-api.yml");

    @AfterAll
    public static void afterAll() { 
        inst.close();
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
    }
}
