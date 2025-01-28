/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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
package eu.openanalytics.crane.test.service;

import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.service.PathReadAccessControlService;
import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathAccessControlServiceTest {

    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static CraneInstance inst;

    @BeforeAll
    public static void beforeAll() {
        keycloakInstance.start();
        inst = new CraneInstance("application-test-api.yml");
    }

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testAccessToRestrictedNetworkRepository() {
        PathReadAccessControlService pathReadAccessControlService = inst.getBean("pathReadAccessControlService", PathReadAccessControlService.class);
        String allowed_ip = "192.168.18.123";
        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setReadAccess(new AccessControl());
        repository.getReadAccess().setNetwork(List.of(allowed_ip));
        repository.validate(false);

        Authentication mockedAuthentication = mock(AnonymousAuthenticationToken.class);
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should not get access incorrect ip");

        mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should not get access wrong not ip");
    }

    @Test
    public void testAccessToRestrictedNetworkAndUserRepository() {
        PathReadAccessControlService pathReadAccessControlService = inst.getBean("pathReadAccessControlService", PathReadAccessControlService.class);
        String allowed_ip = "192.168.18.123";

        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setReadAccess(new AccessControl());
        repository.getReadAccess().setNetwork(List.of(allowed_ip));
        repository.getReadAccess().setUsers(List.of("demo"));
        repository.validate(false);

        Authentication mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct ip and user");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct user but not ip");

        when(mockedAuthentication.getName()).thenReturn("test");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should not get access wrong user and ip");
    }

    @Test
    public void testAccessToRestrictedNetworkAndAnyAuthenticatedUserRepository() {
        PathReadAccessControlService pathReadAccessControlService = inst.getBean("pathReadAccessControlService", PathReadAccessControlService.class);
        String allowed_ip = "192.168.18.123";

        PathComponent repository = new Repository();
        repository.setName("network_restricted_repo");
        repository.setReadAccess(new AccessControl());
        repository.getReadAccess().setNetwork(List.of(allowed_ip));
        repository.getReadAccess().setAnyAuthenticatedUser(true);

        Authentication mockedAuthentication = mock(Authentication.class);
        when(mockedAuthentication.getName()).thenReturn("demo");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct ip and user is authenticated");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access as user is authenticated");

        when(mockedAuthentication.getName()).thenReturn("test");
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access user is authenticated");

        mockedAuthentication = mock(AnonymousAuthenticationToken.class);
        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails(allowed_ip, null));
        Assertions.assertTrue(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should get access correct ip");

        when(mockedAuthentication.getDetails()).thenReturn(new WebAuthenticationDetails("11.11.11.11", null));
        Assertions.assertFalse(pathReadAccessControlService.canAccess(mockedAuthentication, repository.getReadAccess()), "Should not get access incorrect ip and anonymous");
    }
}
