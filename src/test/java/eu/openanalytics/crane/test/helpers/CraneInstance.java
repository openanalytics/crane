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
package eu.openanalytics.crane.test.helpers;

import com.redis.testcontainers.RedisContainer;
import eu.openanalytics.crane.CraneApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sts.StsClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CraneInstance {

    public final CraneClient client;
    @Container
    public final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:6.2.6"));
    public final int port;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Thread thread;
    private final boolean isRunningRedis;
    private ConfigurableApplicationContext app;
    private final String configName;

    public CraneInstance(String configFileName) {
        this(configFileName, 7271, new HashMap<>(), true);
    }

    public CraneInstance(String configFileName, int port) {
        this(configFileName, port, new HashMap<>(), true);
    }

    public CraneInstance(String configFileName, boolean setupKeycloak) {
        this(configFileName, 7271, new HashMap<>(), setupKeycloak);
    }

    public CraneInstance(String configFileName, Map<String, String> properties) {
        this(configFileName, 7271, properties, true);
    }

    public CraneInstance(String configFileName, int port, Map<String, String> properties, boolean setupKeycloak) {
        this.configName = configFileName;
        try {
            this.port = port;
            int mgmtPort = port % 1000 + 9000;

            SpringApplication application = new SpringApplication(CraneApplication.class);
            application.addPrimarySources(Collections.singletonList(TestConfiguration.class));
            Properties allProperties = CraneApplication.getDefaultProperties();
            allProperties.put("spring.config.location", "src/test/resources/" + configFileName);
            allProperties.put("server.port", port);
            allProperties.put("management.server.port", mgmtPort);
            allProperties.put("proxy.kubernetes.namespace", "test");
            if (setupKeycloak) {
                allProperties.put("app.openid-issuer-uri", KeycloakInstance.getURI());
                allProperties.put("spring.security.oauth2.client.provider.crane.issuer-uri", KeycloakInstance.getURI());
            }
            allProperties.put("spring.security.oauth2.client.registration.crane.client-id", "crane_client");
            allProperties.put("spring.security.oauth2.client.registration.crane.client-secret", "secret");
            allProperties.put("spring.security.oauth2.client.registration.crane.scope", "openid");
            allProperties.putAll(properties);
            isRunningRedis = allProperties.get("spring.session.store-type").equals("redis");
            if (isRunningRedis) {
                redis.setPortBindings(List.of("6379:6379"));
                redis.start();
            }
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
            // Crane may not be ready when it starts serving requests
            Thread.sleep(5_000);
        } catch (Throwable t) {
            closeRedis();
            throw new TestHelperException("Error during startup of Crane", t);
        }
    }

    public static void addInstanceWithAwsAccess(List<CraneInstance> instances, String configName, int port, Logger logger) {
        String awsProfile = System.getenv("AWS_PROFILE");
        if (awsProfile != null && awsProfile.equals("oa-poc")) {
            try (StsClient stsClient = StsClient.create()) {
                stsClient.getCallerIdentity();
                instances.add(new CraneInstance(configName, port));
            }
        }
        logger.warn("No AWS credentials - skipping s3 tests");
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return app.getBean(name, requiredType);
    }

    private void copyResourcesToTmp() {
        String source = "src/test/resources/repository";
        String destination = "/tmp/repository/";
        try {
            if (Files.isDirectory(Path.of(destination))) {
                FileUtils.cleanDirectory(new File(destination));
            }
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

    private void closeRedis() {
        if (isRunningRedis) {
            redis.stop();
            redis.close();
        }
    }

//    @Override
    public void close() {
        app.stop();
        app.close();
        closeRedis();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String toString() {
        return this.configName;
    }
}
