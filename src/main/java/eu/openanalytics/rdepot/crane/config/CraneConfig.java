package eu.openanalytics.rdepot.crane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Component
@ConfigurationProperties(prefix = "app")
public class CraneConfig {

    private String storageLocation;

    private String jwksUri;

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

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    @PostConstruct
    public void init() {
        if (storageLocation == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.storage-location not set");
        }

        if (jwksUri == null) {
            throw new IllegalArgumentException("Incorrect configuration detected: app.jwks-uri not set");
        }
    }
}


