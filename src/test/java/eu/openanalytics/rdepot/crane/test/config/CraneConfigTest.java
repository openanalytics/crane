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
package eu.openanalytics.rdepot.crane.test.config;

import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.rdepot.crane.test.helpers.TestHelperException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.exception.ExceptionUtils;

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
        Assertions.assertEquals(rootCause.getClass(), IllegalArgumentException.class);
        Assertions.assertEquals(rootCause.getMessage(), "Incorrect configuration detected: app.storage-location not set");
    }

    @Test
    public void testConfigurationWithoutOpenidIssuerUri() {
        TestHelperException exception = Assertions.assertThrows(
                TestHelperException.class,
                () -> new CraneInstance("application-no-openid-issuer-uri.yml", false)
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(rootCause.getClass(), IllegalArgumentException.class);
        Assertions.assertEquals(rootCause.getMessage(), "Incorrect configuration detected: app.openid-issuer-uri not set");
    }

    @Test
    public void testConfigurationWithoutAnyRepositories() {
        TestHelperException exception = Assertions.assertThrows(
                TestHelperException.class,
                () -> new CraneInstance("application-no-repositories.yml")
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(rootCause.getClass(), IllegalArgumentException.class);
        Assertions.assertEquals(rootCause.getMessage(), "Incorrect configuration detected: no repositories configured");
    }

    @Test
    public void testConfigurationWithAPublicRepositoryWithAPrivateParent() {
        TestHelperException exception = Assertions.assertThrows(
                TestHelperException.class,
                () -> {
                    new CraneInstance("application-with-public-repository-in-private-parent.yml");
                }
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
                "PathComponent public_repo is invalid, cannot have a public repository (public_repo) in a private parent (private_repo)",
                rootCause.getMessage()
        );
    }

    @Test
    public void testConfigurationWithAPublicRepositoryWithAPrivateParentDeeperNesting() {
        TestHelperException exception = Assertions.assertThrows(
                TestHelperException.class,
                () -> {
                    new CraneInstance("application-with-public-repository-in-private-parent-deeper-nesting.yml");
                }
        );
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        Assertions.assertEquals(IllegalArgumentException.class, rootCause.getClass());
        Assertions.assertEquals(
                "PathComponent abc is invalid, cannot have a public repository (abc) in a private parent (private_repo)",
                rootCause.getMessage()
        );
    }
}
