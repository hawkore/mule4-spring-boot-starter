/*
 * Copyright 2020 HAWKORE, S.L.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkore.springframework.boot.mule.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

/**
 * StorageUtilsTest
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class StorageUtilsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageUtilsTest.class);

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempMultipartNull() {
        StorageUtils.storeArtifactTemp(null);
    }

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempMultipartSecurity() {
        StorageUtils.storeArtifactTemp(new MockMultipartFile("../file", new byte[0]));
    }

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempSecurity() {
        StorageUtils.storeArtifactTemp("../afile", new ByteArrayInputStream(new byte[0]));
    }

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempEmptyName() {
        StorageUtils.storeArtifactTemp("", new ByteArrayInputStream(new byte[0]));
    }

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempNullName() {
        StorageUtils.storeArtifactTemp(null, new ByteArrayInputStream(new byte[0]));
    }

    @Test(expected = DeployArtifactException.class)
    public void storeArtifactTempIO() throws IOException {
        StorageUtils.storeArtifactTemp("name", null);
    }

    @Test
    public void ensureDirectoryExists() throws IOException {
        Path temporalDir = Files.createTempDirectory("_testStorageUtilsTest");
        StorageUtils.ensureDirectoryExists(temporalDir.toFile());
        Assert.assertTrue(temporalDir.toFile().exists());
    }

    @Test(expected = IOException.class)
    public void ensureDirectoryExistsKO_IN_EXISTING_FILE() throws IOException {
        Path temporalFile = Files.createTempFile("_testStorageUtilsTest", "_test");
        StorageUtils.ensureDirectoryExists(temporalFile.toFile());
    }

    @Test(expected = IOException.class)
    public void ensureDirectoryExistsKO_WITH_INVALID_NAME() throws IOException {
        Path temporalDir = Files.createTempDirectory("_testStorageUtilsTest");
        StorageUtils.ensureDirectoryExists(new File(temporalDir.toFile(), "\0"));
    }

    @Test(expected = IOException.class)
    public void verifyZipFilePathsSecurityRoot() throws IOException {
        ZipEntry zipEntry = new ZipEntry("/root/file");
        StorageUtils.verifyZipFilePaths(zipEntry);
    }

    @Test(expected = IOException.class)
    public void verifyZipFilePathsSecurityRelative() throws IOException {
        ZipEntry zipEntry = new ZipEntry("../root/file");
        StorageUtils.verifyZipFilePaths(zipEntry);
    }

    @Test(expected = IOException.class)
    public void verifyZipFilePathsSecurityRelative2() throws IOException {
        ZipEntry zipEntry = new ZipEntry("./../root/file");
        StorageUtils.verifyZipFilePaths(zipEntry);
    }

}
