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
package eu.openanalytics.crane.test.upload;

import com.google.common.io.Files;
import eu.openanalytics.crane.test.api.RepositoryHostingHandlerTest;
import eu.openanalytics.crane.test.helpers.ApiTestHelper;
import eu.openanalytics.crane.test.helpers.CraneInstance;
import eu.openanalytics.crane.test.helpers.KeycloakInstance;
import eu.openanalytics.crane.test.helpers.Response;
import org.carlspring.cloud.storage.s3fs.S3Factory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.StsException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Testcontainers
public class UploadControllerTest {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryHostingHandlerTest.class);
    private static final KeycloakInstance keycloakInstance = new KeycloakInstance();
    private static List<CraneInstance> instances = new ArrayList<>();
    private static S3Client client;
    private static final String bucket = "oa-test-crane-bucket";

    @BeforeAll
    public static void beforeAll() throws URISyntaxException, IOException {
        keycloakInstance.start();
        instances.add(new CraneInstance("application-test-api.yml"));
        if (CraneInstance.addInstanceWithAwsAccess(instances, "application-test-api-with-s3.yml", 7275, logger)) {
            ListObjectsV2Request initialRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build();

            ListObjectsV2Iterable pagination = getClient().listObjectsV2Paginator(initialRequest);
            List<String> filesToDelete = List.of("repository/public_repo/testUpload.txt", "repository/public_repo/public_in_public_repo/testUpload.txt");
            pagination.forEach(listObjectsV2Response -> {
                listObjectsV2Response.contents().forEach(s3Object -> {
                    if (filesToDelete.contains(s3Object.key())) {
                        deleteS3Object(s3Object);
                    }
                });
            });
        }
    }


    static List<CraneInstance> instances() {
        return instances;
    }

    @AfterAll
    public static void afterAll() {
        instances.forEach(CraneInstance::close);
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/testUpload.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @MethodSource("instances")
    public void testUploadToNestedPublicRepository(CraneInstance instance) throws IOException {
        ApiTestHelper apiTestHelper = ApiTestHelper.from(instance);
        String path = "/public_repo/public_in_public_repo/testUpload.txt";
        Path fileToUpload = Path.of("src", "test", "resources", "testUpload.txt");
        apiTestHelper.callWithoutAuth(apiTestHelper.createMultiPartRequest(path, fileToUpload)).assertSuccess();
        Response response = apiTestHelper.callWithoutAuth(apiTestHelper.createHtmlRequest(path));
        response.assertSuccess();
        Assertions.assertEquals(response.body(), new String(Files.toByteArray(fileToUpload.toFile()), StandardCharsets.UTF_8));
    }

    private static void deleteS3Object(S3Object s3Object) {
        String key = s3Object.key();
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        DeleteObjectResponse response = getClient().deleteObject(deleteObjectRequest);
        if (response != null) {
            logger.info("{} was deleted", key);
        } else {
            throw new RuntimeException("An S3 exception occurred during delete");
        }
    }

    private static S3Client getClient() {
        if (client == null) {
            client = S3Client.create();
        }
        return client;
    }

}
