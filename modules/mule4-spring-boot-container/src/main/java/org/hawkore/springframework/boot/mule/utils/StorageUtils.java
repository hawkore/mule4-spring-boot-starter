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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.mule.runtime.core.api.util.FileUtils;
import org.mule.runtime.core.api.util.compression.InvalidZipFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.IOUtils.copy;

/**
 * Storage utils.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class StorageUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageUtils.class);

    private StorageUtils() {}

    /**
     * Unzip.
     *
     * @param archive
     *     the archive
     * @param directory
     *     the directory
     * @throws IOException
     *     the io exception
     */
    public static void unzip(InputStream archive, File directory) throws IOException {

        ensureDirectoryExists(directory);

        try (ZipInputStream zip = new ZipInputStream(archive)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {

                verifyZipFilePaths(entry);

                File f = FileUtils.newFile(directory, entry.getName());

                if (entry.isDirectory()) {
                    ensureDirectoryExists(f);
                } else {
                    File file = new File(directory, entry.getName());
                    // ensure parent directory exists
                    ensureDirectoryExists(file.getParentFile());
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(f))) {
                        copy(zip, os);
                    }
                }
            }
        }
    }

    /**
     * Verify zip file paths.
     *
     * @param entry
     *     the entry
     * @throws InvalidZipFileException
     *     the invalid zip file exception
     */
    /* checks ZipEntry security */
    static void verifyZipFilePaths(ZipEntry entry) throws InvalidZipFileException {
        Path namePath = Paths.get(entry.getName());
        if (namePath.getRoot() != null) {
            // According to .ZIP File Format Specification (Section 4.4.17), the path can not be absolute
            throw new InvalidZipFileException("Absolute paths are not allowed: " + namePath.toString());
        } else if (namePath.normalize().toString().startsWith("..")) {
            // Not specified, but presents a security risk (allows overwriting external files)
            throw new InvalidZipFileException("External paths are not allowed: " + namePath.toString());
        }
    }

    /**
     * Ensure directory exists.
     *
     * @param directory
     *     the directory
     * @throws IOException
     *     the io exception
     */
    static void ensureDirectoryExists(File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Provided directory is not a directory: " + directory);
            }
        } else {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory: " + directory);
            }
        }
    }

    /**
     * Save artifact as a temporal file.
     *
     * @param file
     *     the file
     * @return the file
     */
    public static File storeArtifactTemp(MultipartFile file) {
        try {
            return storeArtifactTemp(file.getOriginalFilename(), file.getInputStream());
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store mule artifact. Please try again!", ex);
        }
    }

    /**
     * Save artifact as a temporal file or return underline file.
     *
     * @param resource
     *     the resource
     * @return the file
     */
    public static File storeArtifactTempOrGet(Resource resource) {
        try {
            if (resource.isFile()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Resource is a file {}, temporal storage is not required",
                        resource.getFile().getAbsolutePath());
                }
                return resource.getFile();
            }
            return storeArtifactTemp(resource.getFilename(), resource.getInputStream());
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store mule artifact. Please try again!", ex);
        }
    }

    /**
     * Save artifact as a temporal file.
     *
     * @param name
     *     the name
     * @param inputStream
     *     the input stream
     * @return the file
     */
    public static File storeArtifactTemp(String name, InputStream inputStream) {
        String fileName = StringUtils.cleanPath(name);
        if (StringUtils.isEmpty(fileName)) {
            throw new DeployArtifactException("You must provide a valid artifact file name. Please try again!");
        }
        try {
            // security check to avoid override system files
            if (fileName.contains("..")) {
                throw new DeployArtifactException("Artifact file name contains invalid characters " + fileName);
            }
            // store on temporal directory
            Path tempPath = Files.createTempDirectory("mule_artifact");
            File aFile = new File(tempPath.toFile(), fileName);
            Files.copy(inputStream, aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Created temporal file for '{}' at {}", fileName, aFile.getAbsolutePath());
            }
            return aFile;
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store artifact file " + fileName + ". Please try again!", ex);
        }
    }

    /**
     * Clean up folder.
     *
     * @param folder
     *     the folder
     */
    public static void cleanUpFolder(File folder) {
        try {
            deleteDirectory(folder);
        } catch (Exception e) {
            LOGGER.warn("Unable to full cleanUpFolder. Error was: {}", e.getMessage());
        }
    }

}
