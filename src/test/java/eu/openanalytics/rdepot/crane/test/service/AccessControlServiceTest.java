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
package eu.openanalytics.rdepot.crane.test.service;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import eu.openanalytics.rdepot.crane.service.AccessControlService;
import org.apache.http.util.Asserts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public class AccessControlServiceTest {
    @Container
    private static final KeycloakContainer keycloakContainer = new KeycloakContainer()
        .withRealmImportFiles("crane-realm.json")
        .withExposedPorts(8080)
        .withExtraHost("localhost", "127.0.0.1");

    @Autowired
    private AccessControlService accessControlService;

    @BeforeAll
    public static void initKeycloak() {
        keycloakContainer.setPortBindings(List.of("8080:8080"));
        keycloakContainer.start();
    }

    @AfterAll
    public static void closeKeycloak() {
        keycloakContainer.stop();
        keycloakContainer.close();
    }
    @Test
    public void testAccessToRestrictedNetworkRepository() {
        String allowed_ip = "192.168.18.123";
        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setAccessNetwork(List.of(allowed_ip));

        Authentication mockedAuthentication = mock(AnonymousAuthenticationToken.class);
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(accessControlService.canAccess(mockedAuthentication, repository), "Should not get access incorrect ip");

        mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(accessControlService.canAccess(mockedAuthentication, repository), "Should not get access wrong not ip");
    }

    @Test
    public void testAccessToRestrictedNetworkAndUserRepository() {
        String allowed_ip = "192.168.18.123";

        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setAccessNetwork(List.of(allowed_ip));
        repository.setAccessUsers(List.of("demo"));

        Authentication mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct ip and user");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct user but not ip");

        when(mockedAuthentication.getName()).thenReturn("test");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(accessControlService.canAccess(mockedAuthentication, repository), "Should not get access wrong user and ip");
    }

    @Test
    public void testAccessToRestrictedNetworkAndAnyAuthenticatedUserRepository() {
        String allowed_ip = "192.168.18.123";

        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setAccessNetwork(List.of(allowed_ip));
        repository.setAccessAnyAuthenticatedUser(true);

        Authentication mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct ip and user is authenticated");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access as user is authenticated");

        when(mockedAuthentication.getName()).thenReturn("test");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access user is authenticated");

        mockedAuthentication = mock(AnonymousAuthenticationToken.class);
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(accessControlService.canAccess(mockedAuthentication, repository), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(accessControlService.canAccess(mockedAuthentication, repository), "Should not get access incorrect ip and anonymous");
    }
}
