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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.awspring.cloud.core.io.s3.SimpleStorageProtocolResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class S3Config {

    public S3Config(ResourceLoader resourceLoader, CraneConfig craneConfig) {
        if (craneConfig.getStorageLocation().startsWith("s3://")) {
            final AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
            if (DefaultResourceLoader.class.isAssignableFrom(resourceLoader.getClass())) {
                ((DefaultResourceLoader) resourceLoader).addProtocolResolver(new SimpleStorageProtocolResolver(s3));
            }
        }
    }
}
