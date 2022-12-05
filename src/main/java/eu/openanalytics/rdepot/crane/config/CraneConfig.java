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
package eu.openanalytics.rdepot.crane.config;

import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "app")
public class CraneConfig {

    private String storageLocation;

    private String openidIssuerUri;

    private String openidGroupsClaim;

    private String openidUsernameClaim = "preferred_username";

    private Map<String, Repository> repositories = new HashMap<>();

    private static final String OIDC_METADATA_PATH = "/.well-known/openid-configuration";


    @PostConstruct
    public void init() {
        if (storageLocation == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location not set");
        }

        if (openidIssuerUri == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.openid-issuer-uri not set");
        }

        if (repositories.size() == 0) {
            throw new IllegalArgumentException("Incorrect configuration detected: no repositories configured");
        }

        repositories.values().forEach(Repository::validate);
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        if (storageLocation.startsWith("s3://")) {
            this.storageLocation = storageLocation;
            return;
        }
        if (!storageLocation.startsWith("/") || !storageLocation.endsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location must either start and end with / or start with s3://");
        }
        File path = new File(storageLocation);
        if (!path.exists() || !path.isDirectory()) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location does not exists or is not a directory");
        }
        this.storageLocation = storageLocation;
    }

    public String getOpenidIssuerUri() {
        return openidIssuerUri;
    }

    public void setOpenidIssuerUri(String openidIssuerUri) {
        this.openidIssuerUri = openidIssuerUri;
    }

    public String getJwksUri() {
        // Create a temporary clientRegistration (with bogus clientId) in order to fetch metadata
        ClientRegistration clientRegistration = ClientRegistrations
            .fromIssuerLocation(getOpenidIssuerUri()).clientId("bogus").build();
        return clientRegistration.getProviderDetails().getJwkSetUri();
    }

    /**
     * @return the openid metadata configuration URL in the same way {@link ClientRegistrations#fromOidcIssuerLocation}
     * builds the URL
     */
    public String getConfiguredOpenIdMetadataUrl() {
        URI issuer = URI.create(openidIssuerUri);

        return UriComponentsBuilder.fromUri(issuer)
            .replacePath(issuer.getPath() + OIDC_METADATA_PATH)
            .build(Collections.emptyMap()).toString();
    }

    public Collection<Repository> getRepositories() {
        return repositories.values();
    }

    public Repository getRepository(String name) {
        return repositories.get(name);
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories.stream()
            .collect(Collectors.toMap(
                Repository::getName,
                it -> it));
    }

    public boolean hasOpenidGroupsClaim() {
        return openidGroupsClaim != null && !openidGroupsClaim.isEmpty();
    }

    public void setOpenidGroupsClaim(String openidGroupsClaim) {
        this.openidGroupsClaim = openidGroupsClaim;
    }

    public String getOpenidGroupsClaim() {
        return openidGroupsClaim;
    }

    public String getOpenidUsernameClaim() {
        return openidUsernameClaim;
    }

    public void setOpenidUsernameClaim(String openidUsernameClaim) {
        this.openidUsernameClaim = openidUsernameClaim;
    }
}
