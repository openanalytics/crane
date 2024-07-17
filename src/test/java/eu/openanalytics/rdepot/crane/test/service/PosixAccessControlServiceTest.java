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

import eu.openanalytics.rdepot.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;
import java.util.List;

@Testcontainers
public class PosixAccessControlServiceTest {
    private static final int cranePort = 7127;
    private static final Logger logger = LoggerFactory.getLogger(PosixAccessControlServiceTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static final GenericContainer craneApp = new GenericContainer(
        new ImageFromDockerfile()
            .withBuildArg("CONFIGURATION", "application.yml")
            .withBuildArg("JAR_LOCATION", "crane.jar")
            .withFileFromPath("application.yml", Paths.get("src","test", "resources", "application-posix-test.yml"))
            .withFileFromPath("crane.jar", Paths.get("target", "crane-0.2.0-SNAPSHOT-exec.jar"))
            .withFileFromClasspath("Dockerfile", "testcontainers/PosixAccessControlDockerfile")
    )
        .withEnv("OPENID_URL", keycloakInstance.getURI())
        .withEnv("CRANE_PORT", String.valueOf(cranePort))
        .withNetwork(keycloakInstance.getNetwork())
        .withExposedPorts(cranePort);


    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        craneApp.setPortBindings(List.of(String.format("%s:%s", cranePort, cranePort)));
        craneApp.withLogConsumer(new Slf4jLogConsumer(logger));
        craneApp.start();
    }

}
