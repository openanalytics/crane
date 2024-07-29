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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig;
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientImpl;

import java.util.List;

public class KeycloakInstance implements AutoCloseable {

    private static String containerIp;
    private static final int exposedPort = 9189;
    private static final Network network = Network.newNetwork();
    @Container
    public static final KeycloakContainer keycloak = new KeycloakContainer()
            .withEnv("KC_HTTP_PORT", String.valueOf(exposedPort))
            .withRealmImportFiles("crane-realm.json")
            .withExposedPorts(exposedPort)
            .withNetwork(network);

    public static boolean isKeycloakRunning = false;

    public void start() {
        if (!isKeycloakRunning) {
            keycloak.setPortBindings(List.of(String.format("%d:%d", exposedPort, exposedPort)));
            keycloak.start();
            containerIp = getKeycloakIp();
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
        return String.format("http://%s:%d/realms/crane", containerIp, exposedPort);
    }

    private static String getKeycloakIp() {
        String networkId = network.getId();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new OkDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
        DockerClient docker = DockerClientImpl.getInstance(config, httpClient);

        com.github.dockerjava.api.model.Network network = docker.inspectNetworkCmd().withNetworkId(networkId).exec();
        String keycloakIp = network.getContainers().get(KeycloakInstance.keycloak.getContainerId()).getIpv4Address();
        return keycloakIp.substring(0, keycloakIp.indexOf('/'));
    }
}
