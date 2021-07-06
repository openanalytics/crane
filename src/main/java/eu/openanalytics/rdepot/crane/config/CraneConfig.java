package eu.openanalytics.rdepot.crane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.util.Collections;

@Component
@ConfigurationProperties(prefix = "app")
public class CraneConfig {

    private String storageLocation;

    private String openidIssuerUri;

    private static final String OIDC_METADATA_PATH = "/.well-known/openid-configuration";

    @PostConstruct
    public void init() {
        if (storageLocation == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location not set");
        }

        if (openidIssuerUri == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.openid-issuer-uri not set");
        }
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        if (!storageLocation.startsWith("/") || !storageLocation.endsWith("/")) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location must start and end with /");
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
}


