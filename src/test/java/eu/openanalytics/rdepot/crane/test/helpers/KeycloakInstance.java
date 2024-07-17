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
package eu.openanalytics.rdepot.crane.test.helpers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;

public class KeycloakInstance implements AutoCloseable {

    private static final String hostname = "localhost";
    private static final int exposedPort = 9189;
    private static final Network network = Network.newNetwork();
    @Container
    public static final KeycloakContainer keycloak = new KeycloakContainer()
        .withEnv("KC_HTTP_PORT", String.valueOf(exposedPort))
        .withRealmImportFiles("crane-realm.json")
        .withExposedPorts(exposedPort)
        .withNetwork(network)
        .withNetworkAliases("keycloak")
        .withExtraHost(hostname, "127.0.0.1");

    public static boolean isKeycloakRunning = false;
    public void start() {
        if (!isKeycloakRunning) {
            keycloak.setPortBindings(List.of(String.format("%d:%d", exposedPort, exposedPort)));
            keycloak.start();
            isKeycloakRunning = true;
        }
    }

    public Network getNetwork() {
        return network;
    }

    @Override
    public void close() {
        if (isKeycloakRunning) {
            keycloak.stop();
            keycloak.close();
        }
    }

    public static String getURI() {
        return String.format("http://keycloak:%d/realms/crane", exposedPort);
    }
}
