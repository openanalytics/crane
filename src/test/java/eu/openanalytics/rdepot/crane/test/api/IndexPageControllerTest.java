package eu.openanalytics.rdepot.crane.test.api;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class IndexPageControllerTest {
    private static final CraneInstance inst = new CraneInstance("application-test-api.yml");

    @AfterAll
    public static void afterAll() { inst.close(); }

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
}
