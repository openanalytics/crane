package eu.openanalytics.rdepot.crane.test.helpers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.openanalytics.rdepot.crane.CraneApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class CraneInstance implements AutoCloseable {

    public final CraneClient client;
    @Container
    public final KeycloakContainer keycloak = new KeycloakContainer()
        .withRealmImportFiles("crane-realm.json")
        .withExposedPorts(8080)
        .withExtraHost("localhost", "127.0.0.1");
    public final int port;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Thread thread;
    private ConfigurableApplicationContext app;

    public CraneInstance(String configFileName) {
        this(configFileName, 7070, new HashMap<>());
    }

    public CraneInstance(String configFileName, int port, Map<String, String> properties) {
        keycloak.setPortBindings(Collections.singletonList("8080:8080"));
        keycloak.start();
        try {
            this.port = port;
            int mgmtPort = port % 1000 + 9000;

            SpringApplication application = new SpringApplication(CraneApplication.class);
            application.addPrimarySources(Collections.singletonList(TestConfiguration.class));
            Properties allProperties = CraneApplication.getDefaultProperties();
            allProperties.put("spring.config.location", "src/test/resources/" + configFileName);
            allProperties.put("server.port", port);
            allProperties.put("management.server.port", mgmtPort);
            allProperties.put("proxy.kubernetes.namespace", "itest");
            allProperties.putAll(properties);
            application.setDefaultProperties(allProperties);

            copyResourcesToTmp();

            client = new CraneClient(port);
            AtomicReference<Throwable> exception = new AtomicReference<>();
            thread = new Thread(() -> app = application.run());
            thread.setUncaughtExceptionHandler((thread, ex) -> {
                exception.set(ex);
            });
            thread.start();

            boolean available = false;
            int attempt = 0;
            int max_attempts = 40;
            while (!available && attempt < max_attempts) {
                delay(attempt);
                if (exception.get() != null) {
                    logger.warn("Exception during startup");
                    available = true;
                    break;
                }
                available = client.checkAlive();
                attempt += 1;
            }

            Throwable ex = exception.get();
            if (ex != null) {
                throw ex;
            }
            if (!available) {
                throw new TestHelperException("Crane did not become available!");
            } else {
                logger.info("Crane available!");
            }
        } catch (Throwable t) {
            throw new TestHelperException("Error during startup of Crane", t);
        }
    }

    private void copyResourcesToTmp() {
        String source = "src/test/resources/repository";
        String destination = "/tmp/repository/";
        try {
            FileUtils.copyDirectory(new File(source), new File(destination));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void delay(int attempt) {
        try {
            if (attempt == 0) {
            } else if (attempt <= 5) {
                Thread.sleep(200);
            } else if (attempt <= 10) {
                Thread.sleep(400);
            } else {
                Thread.sleep(2_000);
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }


    @Override
    public void close() {
        app.stop();
        app.close();
        keycloak.stop();
        keycloak.close();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

}
