/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.test.config;

import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.crane.test.helpers.TestHelperException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.shaded.org.apache.commons.lang3.exception.ExceptionUtils;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class CraneConfigTest {

    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
    }

    @Test
    public void testConfigurationWithMissingStoragePath() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-no-storage-location.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals("Incorrect configuration detected: app.storage-location not set", rootCause.getMessage());
    }

    @Test
    public void testConfigurationWithInvalidRootStorageLocation() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-storage-location.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals("Incorrect configuration detected: app.storage-location must either start and end with / OR start with s3:// and end with /", rootCause.getMessage());
    }

    @Test
    public void testConfigurationWithInvalidRepositoryStorageLocation() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-repository-storage-location.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals("Incorrect configuration detected: app.repositories[repository_with_invalid_storage_location].storage-location must either start and end with / OR start with s3:// and end with /", rootCause.getMessage());
    }

    @Test
    public void testConfigurationWithoutOpenidIssuerUri() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-no-openid-issuer-uri.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals("Incorrect configuration detected: app.openid-issuer-uri not set", rootCause.getMessage());
    }

    @Test
    public void testConfigurationWithoutAnyRepositories() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-no-repositories.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals("Incorrect configuration detected: no repositories configured", rootCause.getMessage());
    }

    @Test
    public void testConfigurationWithAPublicRepositoryWithAPrivateParent() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-with-public-repository-in-private-parent.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
            "PathComponent public_repo has invalid read access control: cannot have a public PathComponent (public_repo) in a private parent (private_repo)",
            rootCause.getMessage()
        );
    }

    @Test
    public void testConfigurationWithAPublicRepositoryWithAPrivateParentDeeperNesting() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-with-public-repository-in-private-parent-deeper-nesting.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
            "PathComponent abc has invalid read access control: cannot have a public PathComponent (abc) in a private parent (private_repo)",
            rootCause.getMessage()
        );
    }

    @Test
    public void testConfigurationWithReadPublicRepoAndPosixEnabled() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-with-posix-and-read-public-repo.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
            "Repository public_and_posix is invalid, cannot add read access control properties to a public repo",
            rootCause.getMessage()
        );
    }

    @Test
    public void testConfigurationWithWritePublicRepoAndPosixEnabled() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-with-posix-and-write-public-repo.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
            "Repository public_and_posix is invalid, cannot add write access control properties to a public repo",
            rootCause.getMessage()
        );
    }

    @Test
    public void testConfigurationWithOnlyPublicRepositoriesAndNoAuth() {
        Assertions.assertDoesNotThrow(
            () -> {
                CraneInstance instance = new CraneInstance("application-test-only-public-repositories.yml", false);
                instance.close();
            }
        );
    }

    @Test
    public void testInvalidConfigurationWithOnlyPublicRepositoriesAndNoAuth() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-test-only-public-repositories.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertTrue(
            rootCause.getMessage().contains("should have public read access when the `only-public` property is `true`.")
        );
    }

    @Test
    public void testInvalidConfigurationWithOnlyPublicRepositoriesAndNoAuthNested() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-test-only-public-repositories-nested.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertTrue(
            rootCause.getMessage().contains("should have public read access when the `only-public` property is `true`.")
        );
    }

    @Test
    public void testInvalidConfigurationWithOnlyPublicRepositoriesAndNoAuthWriteAccess() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-test-only-public-repositories-write-access.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertTrue(
            rootCause.getMessage().contains("should not have write access defined when the `only-public` property is `true`.")
        );
    }

    @Test
    public void testInvalidConfigurationWithOnlyPublicRepositoriesAndNoAuthNestedWriteAccess() {
        TestHelperException exception = Assertions.assertThrows(
            TestHelperException.class,
            () -> new CraneInstance("application-invalid-test-only-public-repositories-nested-write-access.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertTrue(
            rootCause.getMessage().contains("should not have write access defined when the `only-public` property is `true`.")
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_PROFILE", matches = "oa-poc")
    public void testConfigurationWithNoAccessToS3Bucket() {
        TestHelperException exception = Assertions.assertThrows(
                TestHelperException.class,
                () -> new CraneInstance("application-test-no-s3-access.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(S3Exception.class, rootCause.getClass());
        Assertions.assertTrue(
                rootCause.getMessage().contains("is not authorized to perform: s3:ListBucket on resource:")
        );
    }
}
