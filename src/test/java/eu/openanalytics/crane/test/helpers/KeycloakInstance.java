/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import lombok.Getter;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.util.List;

public class KeycloakInstance implements BeforeAllCallback {

    private static final String RE_USABLE_NETWORK_NAME = "565a287f-0771-47ee-9649-aa2f450b2e1a";
    private static final int exposedPort = 9189;

    private static boolean initialized = false;
    private static String containerIp;

    public static KeycloakInstance inst;

    @Getter
    private final Network network = getOrCreateNetwork();

    @Container
    private final KeycloakContainer keycloak = new KeycloakContainer()
            .withEnv("KC_HTTP_PORT", String.valueOf(exposedPort))
            .withRealmImportFiles("crane-realm.json")
            .withExposedPorts(new Integer[]{8080, 8443, 9000, exposedPort})
            .withNetwork(network)
            .withReuse(true); // todo opt-in


    public String getURI() {
        return String.format("http://%s:%d/realms/crane", containerIp, exposedPort);
    }

    private String getKeycloakIp() {
        String networkId = network.getId();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new OkDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
        DockerClient docker = DockerClientImpl.getInstance(config, httpClient);

        com.github.dockerjava.api.model.Network network = docker.inspectNetworkCmd().withNetworkId(networkId).exec();
        String keycloakIp = network.getContainers().get(keycloak.getContainerId()).getIpv4Address();
        return keycloakIp.substring(0, keycloakIp.indexOf('/'));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!initialized) {
            keycloak.setPortBindings(List.of(String.format("%d:%d", exposedPort, exposedPort)));
            keycloak.start();
            containerIp = getKeycloakIp();
            initialized = true;
            inst = this;
        }
    }

    private Network getOrCreateNetwork() {
        if (!TestcontainersConfiguration.getInstance().environmentSupportsReuse()) {
            return Network.newNetwork();
        }
        return createReusableNetwork();
    }

    private Network createReusableNetwork() {
        String networkName = RE_USABLE_NETWORK_NAME;
        Network network = new Network() {
            @Override
            public String getId() {
                return networkName;
            }

            @Override
            public void close() {}

            @Override
            public Statement apply(Statement base, Description description) {
                return null;
            }
        };

        List<com.github.dockerjava.api.model.Network> networks = DockerClientFactory
            .lazyClient()
            .listNetworksCmd()
            .withNameFilter(networkName)
            .exec();
        if (networks.isEmpty()) {
            Network.builder().createNetworkCmdModifier(cmd -> cmd.withName(networkName)).build().getId();
        }
        return network;
    }

}
