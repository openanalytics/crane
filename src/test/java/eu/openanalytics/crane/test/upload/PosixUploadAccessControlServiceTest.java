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
package eu.openanalytics.crane.test.upload;

import com.google.common.io.Files;
import eu.openanalytics.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.crane.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Testcontainers
public class PosixUploadAccessControlServiceTest {
    private static final int cranePort = 7127;
    private static final String targetDirectory = "/tmp/target";
    private static final Logger logger = LoggerFactory.getLogger(PosixUploadAccessControlServiceTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static GenericContainer craneApp = new GenericContainer();
    private final String craneUrl = String.format("http://%s:%s", craneApp.getHost(), cranePort);

    @BeforeAll
    public static void beforeAll() throws IOException, InterruptedException {
        buildJar();
        keycloakInstance.start();

        craneApp = new GenericContainer(new ImageFromDockerfile()
                .withBuildArg("CONFIGURATION", "application.yml")
                .withBuildArg("JAR_LOCATION", "crane.jar")
                .withFileFromPath("application.yml", Paths.get("src", "test", "resources", "application-posix-upload-test.yml"))
                .withFileFromPath("crane.jar", Path.of(targetDirectory + "/crane-exec.jar"))
                .withFileFromClasspath("Dockerfile", "testcontainers/PosixUploadAccessControlDockerfile")
        )
                .withEnv("OPENID_URL", KeycloakInstance.getURI())
                .withEnv("CRANE_PORT", String.valueOf(cranePort))
                .withNetwork(keycloakInstance.getNetwork())
                .withExposedPorts(cranePort);
        craneApp.setPortBindings(List.of(String.format("%s:%s", cranePort, cranePort)));
        craneApp.withLogConsumer(new Slf4jLogConsumer(logger));
        craneApp.start();
    }
// What to do with posix permissions on upload (take parent permissions, can this be done without sudo?)
// Posix access controller flag should be moved to the pathComponent or even AccessControl level so that write and read can operate differently?
    private static void buildJar() throws IOException, InterruptedException {
        File target = new File(targetDirectory);
        if (!target.exists() && !target.mkdir()) {
            throw new RuntimeException("Alternative target directory could not be created");
        }

        Process process = new ProcessBuilder()
                .command("mvn", "-B", "-U", "clean", "package", "-DskipTests=true", "-Dlicense.skip", "-PtmpOutputDir", "-DfinalName=crane")
                .start();

        List<String> info = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
        info.forEach(logger::info);

        List<String> errors = IOUtils.readLines(process.getErrorStream(), Charset.defaultCharset());
        errors.forEach(logger::error);

        if (process.waitFor() != 0) {
            throw new RuntimeException("Build of the project failed");
        }
    }

    @AfterAll
    public static void afterAll() {
        craneApp.stop();
        craneApp.close();
    }

    @Test
    public void testAccessToPublicRepository() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/repository_with_paths/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path)).assertUnauthorizedRedirectToLogIn();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testIndexPageOfPrivateRepository() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/private/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToGroupRepositoryWithMultipleUsers() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/only_group_mathematicians_gid/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToRepositoryWithOwnerRestrictionAccess() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/only_owner_demo/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToRepositoryRestrictedToGroupOfSingleUser() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/only_group_scientists/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToRepositoryWithNoAccessInPosix() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/no_posix_access/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToRepositoryWithNoPathAccess() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/restricted_to_groups_repo/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertNotFound();

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertNotFound();
    }

    @Test
    public void testAccessToNestedGroupRepositoryWithMultipleUsers() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/repository_with_paths/only_group_mathematicians_gid/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        response = apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAccessToNestedRepositoryWithOwnerRestrictionAccess() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/repository_with_paths/only_owner_demo/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
    }

    @Test
    public void testAccessToNestedRepositoryRestrictedToGroupOfSingleUser() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/repository_with_paths/only_group_scientists/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
//        Response response = apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createHtmlRequest(path));
//        response.assertSuccess();
//        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
    }

    @Test
    public void testAccessToNestedRepositoryWithNoPathAccess() throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String genericPath = "/repository_with_paths/restricted_to_groups_repo/testUpload_%s.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");

        String path = genericPath.formatted("demo");
        apiTestHelper.callWithTokenAuthDemoUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();

        path = genericPath.formatted("unauthorized");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertUnauthorized();

        path = genericPath.formatted("test");
        apiTestHelper.callWithTokenAuthTestUser(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertForbidden();
    }

    // TODO: add tests to throw error that crane does not have the permissions to create the requested file
}
