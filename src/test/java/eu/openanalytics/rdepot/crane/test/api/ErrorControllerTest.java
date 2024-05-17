package eu.openanalytics.rdepot.crane.test.api;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ErrorControllerTest {
    private static final CraneInstance inst = new CraneInstance("application-test-api.yml");

    @AfterAll
    public static void afterAll() { inst.close(); }

    @Test
    public void testCacheHeaders() {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
        String repository = "/private_repo";
        String file = repository + "/file.txt";

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertHasNoCachingHeader();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertHasNoCachingHeader();
    }
}
