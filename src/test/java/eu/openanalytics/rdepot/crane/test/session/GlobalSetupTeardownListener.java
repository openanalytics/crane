package eu.openanalytics.rdepot.crane.test.session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class GlobalSetupTeardownListener implements LauncherSessionListener {

    private Fixture fixture;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Avoid setup for test discovery by delaying it until tests are about to be executed
        session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
            @Override
            public void testPlanExecutionStarted(TestPlan testPlan) {
                if (fixture == null) {
                    fixture = new Fixture();
                    fixture.setUp();
                }
            }
        });
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (fixture != null) {
            fixture.tearDown();
            fixture = null;
        }
    }

    static class Fixture {

        private final KeycloakInstance keycloakInstance = new KeycloakInstance();
        private ExecutorService executorService;

        void setUp() {
            keycloakInstance.start();
            executorService = Executors.newCachedThreadPool();
        }

        void tearDown() {
            keycloakInstance.close();
            executorService.shutdownNow();
        }
    }

}

