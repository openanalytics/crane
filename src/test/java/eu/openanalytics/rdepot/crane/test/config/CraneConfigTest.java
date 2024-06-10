package eu.openanalytics.rdepot.crane.test.config;

import eu.openanalytics.rdepot.crane.test.helpers.CraneInstance;
import eu.openanalytics.rdepot.crane.test.helpers.TestHelperException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.exception.ExceptionUtils;

public class CraneConfigTest {

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
            () -> new CraneInstance("application-no-openid-issuer-uri.yml")
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
}
