package eu.openanalytics.rdepot.crane.test.service;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
public class AuditingServiceTest {
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;
    private static final File auditLogsFile = new File("/tmp/auditingLogs.txt");
    private static BufferedReader bufferedReader;

    @BeforeAll
    public static void beforeAll() {
        clearAuditLoggingFile();
        try {
            bufferedReader = new BufferedReader(new FileReader(auditLogsFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        keycloakInstance.start();
        Map<String, String> properties = new HashMap<>();
        properties.put("app.audit-logging", auditLogsFile.getAbsolutePath());
        inst = new CraneInstance("application-test-api.yml", properties);
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testAuditingEventIndexPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        String anonymousIndexPage = ReadNextLineWithDelay();
        checkLoggingLine(anonymousIndexPage, "/", "LIST_REPOSITORIES");

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/"));
        String demoAuthentication = ReadNextLineWithDelay();
        String demoIndexPage = ReadNextLineWithDelay();
        checkLoggingLine(demoAuthentication,"/", "AUTHENTICATION_SUCCESS", "demo");
        checkLoggingLine(demoIndexPage,"/", "LIST_REPOSITORIES", "demo");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        String testAuthentication = ReadNextLineWithDelay();
        String testIndexPage = ReadNextLineWithDelay();
        checkLoggingLine(testAuthentication,"/", "AUTHENTICATION_SUCCESS", "test");
        checkLoggingLine(testIndexPage,"/", "LIST_REPOSITORIES", "test");
    }

    private String ReadNextLineWithDelay() throws IOException, InterruptedException {
        Thread.sleep(50);
        return bufferedReader.readLine();
    }

    @Test
    public void testAuditingUnauthorizedEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/private_repo"));
        String anonymousAuthorization = ReadNextLineWithDelay();
        checkLoggingLine(anonymousAuthorization, "/private_repo", "AUTHORIZATION_FAILURE");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/restricted_repo"));
        String testAuthentication = ReadNextLineWithDelay();
        String testAuthorization = ReadNextLineWithDelay();
        checkLoggingLine(testAuthentication,"/restricted_repo", "AUTHENTICATION_SUCCESS", "test");
        checkLoggingLine(testAuthorization,"/restricted_repo", "AUTHORIZATION_FAILURE", "test");
    }

    @Test
    public void testAuditingLogoutEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/logout"));
        String anonymousLogout = ReadNextLineWithDelay();
        checkLoggingLine(anonymousLogout, "/logout", "LOGOUT");

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/logout"));
        String demoLogout = ReadNextLineWithDelay();
        String demoAuthentication = ReadNextLineWithDelay();
        checkLoggingLine(demoLogout, "/logout", "LOGOUT");
        checkLoggingLine(demoAuthentication,"/logout-success", "AUTHENTICATION_SUCCESS", "demo");

        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/logout"));
        String testLogout = ReadNextLineWithDelay();
        String testAuthentication = ReadNextLineWithDelay();
        checkLoggingLine(testLogout, "/logout", "LOGOUT");
        checkLoggingLine(testAuthentication,"/logout-success", "AUTHENTICATION_SUCCESS", "test");
    }

    @Test
    public void testAuditingErrorHandlerEventPage() throws IOException, InterruptedException {
        ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/undefined_repository"));
        String authentication_line = ReadNextLineWithDelay();
        String authorization_line = ReadNextLineWithDelay();

        checkLoggingLine(authentication_line,"/undefined_repository", "AUTHENTICATION_SUCCESS", "demo");
        checkLoggingLine(authorization_line,"/undefined_repository", "AUTHORIZATION_FAILURE", "demo");
    }
    private void checkLoggingLine(String line, String path, String type, String username) {
        assertContainsPrincipalWithUsername(line, username);
        assertContainsPath(line, path);
        assertContainsType(line, type);
    }

    private void checkLoggingLine(String line, String path, String type) {
        checkLoggingLine(line, path, type, "anonymousUser");
    }

    private void assertContainsPrincipalWithUsername(String line, String username) {
        Assertions.assertTrue(line.contains(String.format("\"principal\":\"%s\"", username)));
    }

    private void assertContainsPath(String line, String path) {
        Assertions.assertTrue(line.contains(String.format("\"request_path\":\"%s\"", path)));
    }

    private void assertContainsType(String line, String type) {
        Assertions.assertTrue(line.contains(String.format("\"type\":\"%s\"", type)));
    }

    private static void clearAuditLoggingFile() {

        if (auditLogsFile.exists() && !auditLogsFile.delete()) {
            throw new RuntimeException("Could not delete auditing file!");
        }
        try {
            if (!auditLogsFile.createNewFile()) {
                throw new RuntimeException("Could not create auditing file!");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
