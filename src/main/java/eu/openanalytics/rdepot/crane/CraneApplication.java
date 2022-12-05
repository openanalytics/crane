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
package eu.openanalytics.rdepot.crane;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.awspring.cloud.core.io.s3.SimpleStorageProtocolResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.annotation.PostConstruct;
import java.util.Properties;

@EnableWebMvc
@SpringBootApplication
public class CraneApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CraneApplication.class);
        app.setDefaultProperties(getDefaultProperties());
        app.run(args);
    }

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void configureS3() {
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
        if (DefaultResourceLoader.class.isAssignableFrom(resourceLoader.getClass())) {
            ((DefaultResourceLoader) resourceLoader).addProtocolResolver(new SimpleStorageProtocolResolver(s3));
        }
    }

    private static Properties getDefaultProperties() {
        Properties properties = new Properties();

        // Health configuration
        // ====================

        properties.put("management.server.port", "9090");

        // disable ldap health endpoint
        properties.put("management.health.ldap.enabled", false);
        // disable default redis health endpoint since it's managed by redisSession
        properties.put("management.health.redis.enabled", false);
        // enable Kubernetes probes
        properties.put("management.endpoint.health.probes.enabled", true);

        // ====================

        return properties;
    }


}
