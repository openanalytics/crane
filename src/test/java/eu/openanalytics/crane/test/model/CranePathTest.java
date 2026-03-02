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
package eu.openanalytics.crane.test.model;

import eu.openanalytics.crane.model.config.AccessControl;
import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.model.config.Repository;
import eu.openanalytics.crane.model.runtime.CranePath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CranePathTest {

    @Test
    public void basicTests() throws IOException {
        Repository repository = new Repository(null,
            null,
            "/tmp/test_repository",
            null,
            false,
            null,
            new AccessControl(null, null, true, null, null, null),
            null,
            null,
            null,
            null);
        repository.setName("test_repository");
        repository.validate(false);
        repository.init(null, Path.of("/tmp/test_repository"));

        // create test directory
        Files.createDirectories(Path.of("/tmp/test_repository/test_path/my/complex/path"));

        CranePath testPath = repository.getPath("test_path/my/complex/path");
        Assertions.assertEquals(Path.of("/tmp/test_repository/test_path/my/complex/path"), testPath.getStoragePath());
        Assertions.assertEquals("test_path/my/complex/path", testPath.getPathRelativeToRepository());
        Assertions.assertEquals(repository, testPath.getRepository());
        Assertions.assertNull(testPath.getPathComponent());
        Assertions.assertFalse(testPath.isExistsInConfig());
        Assertions.assertTrue(testPath.isDirectory());
        Assertions.assertEquals(new CranePath(repository, "test_path/my/complex/path/index.html"), testPath.getIndexFile());
        Assertions.assertFalse(testPath.isAnIndexFile());
        Assertions.assertTrue(testPath.getIndexFile().isAnIndexFile());
        Assertions.assertTrue(testPath.isExists());
        Assertions.assertEquals(new CranePath(repository, "test_path/my/complex"), testPath.getParent());
        Assertions.assertEquals(new CranePath(repository, "test_path/my"), testPath.getParent().getParent());
        Assertions.assertEquals(new CranePath(repository, "test_path"), testPath.getParent().getParent().getParent());
        Assertions.assertEquals(new CranePath(repository, ""), testPath.getParent().getParent().getParent().getParent());
        Assertions.assertNull(testPath.getParent().getParent().getParent().getParent().getParent());

        Exception exception = Assertions.assertThrows(IllegalStateException.class, testPath::getMediaType);
        Assertions.assertEquals("CranePath is a directory and has no MediaType", exception.getMessage());

        Assertions.assertEquals(0, testPath.getContents().count());
        Assertions.assertEquals("/test_repository/test_path/my/complex/path", testPath.getWebUri());
        Assertions.assertEquals("path", testPath.getName());
        Assertions.assertEquals(List.of(
                new CranePath(repository, "test_path"),
                new CranePath(repository, "test_path/my"),
                new CranePath(repository, "test_path/my/complex"),
                new CranePath(repository, "test_path/my/complex/path")
            ),
            testPath.getComponents()
        );
        Assertions.assertNull(testPath.getReadAccess());
        Assertions.assertNull(testPath.getWriteAccess());
        Assertions.assertNotNull(testPath.getBasicFileAttributes());
        Assertions.assertNotNull(testPath.getLastModifiedTime());
        Assertions.assertTrue(testPath.getLastModifiedTimeInMillis() > 0);
        // don't check exact size, size depends on platform
        Assertions.assertNotNull(testPath.getSize());
        Assertions.assertNotNull(testPath.getFormattedSize());

        exception = Assertions.assertThrows(IllegalStateException.class, testPath::asInputStremam);
        Assertions.assertEquals("CranePath is a directory and cannot be opened as InputStream", exception.getMessage());

        Assertions.assertFalse(testPath.getPath("test.index.html").isAnIndexFile());
    }

    @Test
    public void testPathComponent() throws IOException {
        AccessControl accessControl1 = new AccessControl(null, null, true, null, null, null);
        AccessControl accessControl2 = new AccessControl(null, null, true, null, null, null);
        AccessControl accessControl3 = new AccessControl(null, List.of("demo"), false, null, null, null);

        PathComponent pathComponent3 = new PathComponent(accessControl3, null, null, null, null);
        PathComponent pathComponent2 = new PathComponent(accessControl2, null, null, null, Map.of("complex", pathComponent3, "unrelated_path", createUnrelatedPathComponent()));
        PathComponent pathComponent1 = new PathComponent(accessControl1, null, null, null, Map.of("my", pathComponent2, "", createUnrelatedPathComponent()));

        Repository repository = new Repository(null,
            null,
            "/tmp/test_repository",
            null,
            false,
            null,
            new AccessControl(null, null, true, null, null, null),
            null,
            null,
            null,
            Map.of(
                "other_path", createUnrelatedPathComponent(),
                "test_path", pathComponent1
            )
        );
        repository.setName("test_repository");
        repository.validate(false);
        repository.init(null, Path.of("/tmp/test_repository"));

        // create test directory
        Files.createDirectories(Path.of("/tmp/test_repository/test_path/my/complex/path"));

        CranePath testPath = repository.getPath("test_path/my/complex/path");
        Assertions.assertNull(testPath.getPathComponent());
        Assertions.assertNull(testPath.getReadAccess());
        Assertions.assertNull(testPath.getWriteAccess());
        Assertions.assertFalse(testPath.isExistsInConfig());

        testPath = repository.getPath("test_path/my/complex");
        Assertions.assertEquals("complex", testPath.getName());
        Assertions.assertEquals(pathComponent3, testPath.getPathComponent());
        Assertions.assertEquals("complex", testPath.getPathComponent().getName());
        Assertions.assertEquals(accessControl3, testPath.getReadAccess());
        Assertions.assertNotNull(testPath.getWriteAccess());
        Assertions.assertTrue(testPath.isExistsInConfig());

        testPath = repository.getPath("test_path/my");
        Assertions.assertEquals("my", testPath.getName());
        Assertions.assertEquals(pathComponent2, testPath.getPathComponent());
        Assertions.assertEquals("my", testPath.getPathComponent().getName());
        Assertions.assertEquals(accessControl2, testPath.getReadAccess());
        Assertions.assertNotNull(testPath.getWriteAccess());
        Assertions.assertTrue(testPath.isExistsInConfig());

        testPath = repository.getPath("test_path");
        Assertions.assertEquals("test_path", testPath.getName());
        Assertions.assertEquals(pathComponent1, testPath.getPathComponent());
        Assertions.assertEquals("test_path", testPath.getPathComponent().getName());
        Assertions.assertEquals(accessControl1, testPath.getReadAccess());
        Assertions.assertNotNull(testPath.getWriteAccess());
        Assertions.assertTrue(testPath.isExistsInConfig());

        testPath = repository.getPath("");
        Assertions.assertEquals("test_repository", testPath.getName());
        Assertions.assertEquals(repository, testPath.getPathComponent());
        Assertions.assertEquals("test_repository", testPath.getPathComponent().getName());
        Assertions.assertEquals(repository.getReadAccess(), testPath.getReadAccess());
        Assertions.assertNotNull(testPath.getWriteAccess());
        Assertions.assertTrue(testPath.isExistsInConfig());
    }

    @Test
    public void testNonExistingPath() throws IOException {
        Repository repository = new Repository(null,
            null,
            "/tmp/test_repository",
            null,
            false,
            null,
            new AccessControl(null, null, true, null, null, null),
            null,
            null,
            null,
            null
        );
        repository.setName("test_repository");
        repository.validate(false);
        repository.init(null, Path.of("/tmp/test_repository"));

        CranePath somePath = new CranePath(repository, "a/path/that/does/not/exist");
        Assertions.assertFalse(somePath.isExists());
        Assertions.assertFalse(somePath.isExistsInConfig());
        Assertions.assertFalse(somePath.isAnIndexFile());
        Assertions.assertFalse(somePath.isDirectory());
        Assertions.assertNull(somePath.getSize());
        Assertions.assertNull(somePath.getFormattedSize());
        Assertions.assertNull(somePath.getLastModifiedTime());
        Assertions.assertNull(somePath.getLastModifiedTimeInMillis());
        Assertions.assertNull(somePath.getBasicFileAttributes());
        Assertions.assertNull(somePath.getMediaType());
        Assertions.assertThrows(IllegalStateException.class, somePath::getContents);
    }

    @Test
    public void testMetadataFile() throws IOException {
        Repository repository = new Repository(null,
            null,
            "/tmp/test_repository",
            null,
            false,
            null,
            new AccessControl(null, null, true, null, null, null),
            null,
            new AccessControl(null, null, true, null, null, null),
            null,
            null
        );
        repository.setName("test_repository");
        repository.validate(false);
        repository.init(null, Path.of("/tmp/test_repository"));

        CranePath root = new CranePath(repository, "");
        // repository is never a metadata file or directory
        Assertions.assertFalse(root.isMetadataFile());
        Assertions.assertFalse(root.isMetadataDirectory());
        Assertions.assertTrue(root.getMetadataReadAccess().isPublic());
        Assertions.assertFalse(root.getMetadataWriteAccess().isPublic());

        CranePath metadataDirectory = new CranePath(repository, ".crane");
        Assertions.assertFalse(metadataDirectory.isMetadataFile());
        Assertions.assertTrue(metadataDirectory.isMetadataDirectory());
        Assertions.assertTrue(metadataDirectory.isExistsInConfig()); // metadata directory always exists in config
        Assertions.assertTrue(metadataDirectory.getMetadataReadAccess().isPublic());
        Assertions.assertFalse(metadataDirectory.getMetadataWriteAccess().isPublic());

        CranePath metadataFile = new CranePath(repository, ".crane/somefile.txt.json");
        Assertions.assertTrue(metadataFile.isMetadataFile());
        Assertions.assertFalse(metadataFile.isMetadataDirectory());
        Assertions.assertFalse(metadataFile.isExistsInConfig());
        Assertions.assertNull(metadataFile.getMetadataReadAccess());
        Assertions.assertNull(metadataFile.getMetadataWriteAccess());
    }

    /**
     * Creates a PathComponent just to fill the config.
     *
     * @return dummy PathComponent
     */
    private PathComponent createUnrelatedPathComponent() {
        AccessControl accessControl = new AccessControl(null, null, true, null, null, null);
        return new PathComponent(accessControl, null, null, null, null);
    }

}
