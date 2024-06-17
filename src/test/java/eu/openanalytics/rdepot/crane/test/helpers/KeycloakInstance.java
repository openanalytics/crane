/**
 * Crane
 *
 * Copyright (C) 2021-2022 Open Analytics
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
package eu.openanalytics.rdepot.crane.test.helpers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;

public class KeycloakInstance implements AutoCloseable {

    private static final String hostname = "localhost";
    @Container
    public static final KeycloakContainer keycloak = new KeycloakContainer()
        .withRealmImportFiles("crane-realm.json")
        .withExposedPorts(8080)
        .withExtraHost(hostname, "127.0.0.1");
    private final String exposedPort = "9189";
    public static boolean isKeycloakRunning = false;
    public void start() {
        if (!isKeycloakRunning) {
            keycloak.setPortBindings(List.of(String.format("%s:8080", exposedPort)));
            System.setProperty("http.keycloak.host", hostname);
            System.setProperty("http.keycloak.port", exposedPort);
            keycloak.start();
            isKeycloakRunning = true;
        }
    }

    @Override
    public void close() {
        if (isKeycloakRunning) {
            keycloak.stop();
            keycloak.close();
        }
    }
}
