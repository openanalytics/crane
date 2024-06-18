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

import eu.openanalytics.rdepot.crane.model.config.CacheRule;
import eu.openanalytics.rdepot.crane.model.config.Repository;
import org.carlspring.cloud.storage.s3fs.S3Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.StsException;

@Component
@ConfigurationProperties(prefix = "app")
public class CraneConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String storageLocation;

    private String auditLogging;

    private Path auditLoggingPath;

    private String openidIssuerUri;

    private String openidLogoutUrl;

    private String openidGroupsClaim;

    private String openidUsernameClaim = "preferred_username";

    private String templatePath;

    private URI s3Endpoint;

    private Map<String, Repository> repositories = new HashMap<>();

    private static final String OIDC_METADATA_PATH = "/.well-known/openid-configuration";
    private Path root;

    private List<CacheRule> defaultCache;

    public Path getRoot() {
        return root;
    }

    @PostConstruct
    public void init() throws IOException, URISyntaxException {
        if (storageLocation == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location not set");
        }

        if (openidIssuerUri == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.openid-issuer-uri not set");
        }

        if (repositories.size() == 0) {
            throw new IllegalArgumentException("Incorrect configuration detected: no repositories configured");
        }

        if (auditLogging != null) {
            FileSystem fs = FileSystems.getFileSystem(new URI("file:///"));
            auditLoggingPath = fs.getPath(new URI(auditLogging).getPath());
        }

        root = storageLocationToPath(storageLocation);

        repositories.values().forEach(Repository::validate);
        repositories.values().forEach(r -> {
            if (defaultCache != null && r.getCache() == null) {
                r.setCache(defaultCache);
            }
            if (r.getStorageLocation() == null) {
                r.setStorageLocation(storageLocation);
                r.setStoragePath(root);
            } else {
                try {
                    r.setStoragePath(storageLocationToPath(r.getStorageLocation()));
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Path storageLocationToPath(String storageLocation) throws IOException, URISyntaxException {
        if (storageLocation.startsWith("s3://")) {
            try (StsClient client = StsClient.create()) {
                try {
                    logger.info("Using AWS identity: " + client.getCallerIdentity().arn());
                } catch (StsException exception) {
                    logger.info("Not authenticated with AWS, enable debug logs in case this unexpected.");
                    logger.debug("Not authenticated with AWS", exception);
                }
            }

            if (s3Endpoint == null) {
                s3Endpoint = new URI("https:///");
            }

            final Map<String, String> env = new HashMap<>();
            env.put(S3Factory.PROTOCOL, s3Endpoint.getScheme());

            URI uri = new URI(storageLocation);
            String path = new URI("/" + uri.getAuthority() + uri.getPath()).getPath();

            try (FileSystem fs = FileSystems.newFileSystem(URI.create("s3:" + s3Endpoint.getSchemeSpecificPart()), env, Thread.currentThread().getContextClassLoader())) {
                return fs.getPath(path);
            }
        } else {
            FileSystem fs = FileSystems.getFileSystem(new URI("file:///"));
            return fs.getPath(new URI(storageLocation).getPath());
        }
    }

    public void setStorageLocation(String storageLocation) {
        if (storageLocation.startsWith("s3://")) {
            if (!storageLocation.startsWith("s3://") || !storageLocation.endsWith("/")) {
                throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location must either start and end with / OR start with s3:// and end with /");
            }
            this.storageLocation = storageLocation; // TODO
            return;
        }
        if (!storageLocation.startsWith("/") || !storageLocation.endsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location must either start and end with / OR start with s3:// and end with /");
        }
        File path = new File(storageLocation);
        if (!path.exists() || !path.isDirectory()) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location does not exists or is not a directory");
        }
        this.storageLocation = storageLocation; // TODO
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

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        if (!templatePath.endsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.template-path must end with /");
        }
        this.templatePath = templatePath;
    }

    public URI getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(URI s3Endpoint) {
        if (!Objects.equals(s3Endpoint.getScheme(), "http") && !Objects.equals(s3Endpoint.getScheme(), "https")) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.s3-endpoint must start with http:// or https://");
        }
        this.s3Endpoint = s3Endpoint;
    }

    public String getOpenidLogoutUrl() {
        return openidLogoutUrl;
    }

    public void setOpenidLogoutUrl(String openidLogoutUrl) {
        this.openidLogoutUrl = openidLogoutUrl;
    }

    public List<CacheRule> getDefaultCache() {
        return defaultCache;
    }

    public void setDefaultCache(List<CacheRule> defaultCache) {
        this.defaultCache = defaultCache;
    }

    public String getAuditLogging() { return auditLogging; }

    public Path getAuditLoggingPath() { return auditLoggingPath; }

    public void setAuditLogging(String auditLogging) {
        this.auditLogging = auditLogging;
    }
}
