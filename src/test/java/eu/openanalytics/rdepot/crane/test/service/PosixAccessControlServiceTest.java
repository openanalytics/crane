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
package eu.openanalytics.rdepot.crane.test.service;

import eu.openanalytics.rdepot.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.rdepot.crane.test.helpers.Response;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Testcontainers
public class PosixAccessControlServiceTest {
    private static final int cranePort = 7127;
    private static final String targetDirectory = "/tmp/target";
    private static final Logger logger = LoggerFactory.getLogger(PosixAccessControlServiceTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static final GenericContainer craneApp = new GenericContainer(
            new ImageFromDockerfile()
                    .withBuildArg("CONFIGURATION", "application.yml")
                    .withBuildArg("JAR_LOCATION", "crane.jar")
                    .withFileFromPath("application.yml", Paths.get("src", "test", "resources", "application-posix-test.yml"))
                    .withFileFromPath("crane.jar", Path.of(targetDirectory + "/crane-0.2.0-SNAPSHOT-exec.jar"))
                    .withFileFromClasspath("Dockerfile", "testcontainers/PosixAccessControlDockerfile")
    )
            .withEnv("OPENID_URL", KeycloakInstance.getURI())
            .withEnv("CRANE_PORT", String.valueOf(cranePort))
            .withNetwork(keycloakInstance.getNetwork())
            .withExposedPorts(cranePort);

    @BeforeAll
    public static void beforeAll() throws IOException, InterruptedException {
        buildJar();
        keycloakInstance.start();
        craneApp.setPortBindings(List.of(String.format("%s:%s", cranePort, cranePort)));
        craneApp.withLogConsumer(new Slf4jLogConsumer(logger));
        craneApp.start();
    }

    private static void buildJar() throws IOException, InterruptedException {
        File target = new File(targetDirectory);
        if (!target.exists() && !target.mkdir()) {
            throw new RuntimeException("Alternative target directory could not be created");
        }

        Process process = new ProcessBuilder()
                .command("mvn", "-B", "-U", "clean", "package", "-DskipTests=true", "-Dlicense.skip", "-PtmpOutputDir")
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

    private final String craneUrl = String.format("http://%s:%s", craneApp.getHost(), cranePort);

    @Test
    public void testIndexPage() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);

        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest("/"));
        String body = resp.body();
        Set<String> repositories = new HashSet<>(Set.of("no_posix_access"));
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories.addAll(Set.of("repository_with_paths", "only_group_mathematicians"));
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest("/"));
        body = resp.body();
        repositories.addAll(Set.of("only_owner_demo", "only_group_scientists"));
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));
    }

    @Test
    public void testIndexPageOfPublicRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String path = "/repository_with_paths";

        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        resp.assertUnauthorizedRedirectToLogIn();

        resp = apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(path));
        String body = resp.body();
        Set<String> repositories = new HashSet<>(Set.of("repository_with_paths", "only_group_mathematicians", "file.txt"));
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(path));
        body = resp.body();
        repositories.addAll(Set.of("only_owner_demo", "only_group_scientists"));
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));
    }

    @Test
    public void testIndexPageOfPrivateRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String path = "/private";

        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path)).assertUnauthorizedRedirectToLogIn();

        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(path));
        String body = resp.body();
        Set<String> repositories = Set.of("file.txt");
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));

        resp = apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(path));
        body = resp.body();
        Assertions.assertTrue(repositories.stream().allMatch(body::contains));
    }

    @Test
    public void testAccessToPublicRepository() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/repository_with_paths";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();


        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToGroupRepositoryWithMultipleUsers() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/only_group_mathematicians";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToRepositoryWithOwnerRestrictionAccess() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/only_owner_demo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToRepositoryRestrictedToGroupOfSingleUser() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/only_group_scientists";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToRepositoryWithNoAccessInPosix() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/no_posix_access";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToRepositoryWithNoPathAccess() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/restricted_to_groups_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToNestedGroupRepositoryWithMultipleUsers() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/repository_with_paths/only_group_mathematicians";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertSuccess();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertSuccess();
    }

    @Test
    public void testAccessToNestedRepositoryWithOwnerRestrictionAccess() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/repository_with_paths/only_owner_demo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToNestedRepositoryRestrictedToGroupOfSingleUser() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/repository_with_paths/only_group_scientists";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertSuccess();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }

    @Test
    public void testAccessToNestedRepositoryWithNoPathAccess() {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(craneUrl);
        String repository = "/repository_with_paths/restricted_to_groups_repo";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(repository)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(repository)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(repository)).assertNotFound();

        String file = repository + "/file.txt";
        apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(file)).assertUnauthorizedRedirectToLogIn();
        apiTestHelper.callWithAuth(apiTestHelper.createHtmlRequest(file)).assertNotFound();
        apiTestHelper.callWithAuthTestUser(apiTestHelper.createHtmlRequest(file)).assertNotFound();
    }
}
