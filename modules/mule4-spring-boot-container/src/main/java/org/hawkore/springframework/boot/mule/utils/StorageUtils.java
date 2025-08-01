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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
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
 * <p>
 * Provides utility methods for safely handling file storage and ZIP extraction,
 * ensuring proper validation and avoiding common security risks like path traversal.
 * <p>
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class StorageUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageUtils.class);

    private StorageUtils() {}

    /**
     * Unzip an archive into the specified directory.
     *
     * @param archive
     *     the ZIP input stream
     * @param directory
     *     the target directory
     * @throws IOException
     *     if an I/O error occurs
     */
    public static void unzip(InputStream archive, File directory) throws IOException {

        ensureDirectoryExists(directory);

        try (ZipInputStream zip = new ZipInputStream(archive)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {

                verifyZipFilePaths(entry);

                File destFile = new File(directory, entry.getName());
                String destCanonicalPath = destFile.getCanonicalPath();
                String targetCanonicalPath = directory.getCanonicalPath();

                if (!destCanonicalPath.startsWith(targetCanonicalPath + File.separator)) {
                    throw new InvalidZipFileException("Entry is outside the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    ensureDirectoryExists(destFile);
                } else {
                    ensureDirectoryExists(destFile.getParentFile());
                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(destFile.toPath()))) {
                        copy(zip, os);
                    }
                }
            }
        }
    }

    /**
     * Verify zip file paths for potential path traversal attacks.
     *
     * @param entry
     *     the ZIP entry
     * @throws InvalidZipFileException
     *     if the path is absolute or attempts to escape the target directory
     */
    static void verifyZipFilePaths(ZipEntry entry) throws InvalidZipFileException {
        Path namePath = Paths.get(entry.getName()).normalize();

        if (namePath.isAbsolute() || namePath.startsWith("..") || entry.getName().contains("..")) {
            throw new InvalidZipFileException("Invalid ZIP entry: " + entry.getName());
        }
    }

    /**
     * Ensure a directory exists, creating it if necessary.
     *
     * @param directory
     *     the directory
     * @throws IOException
     *     if the directory does not exist and cannot be created
     */
    static void ensureDirectoryExists(File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Provided path is not a directory: " + directory);
            }
        } else if (!directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory);
        }
    }

    /**
     * Save artifact as a temporary file.
     *
     * @param file
     *     the multipart file
     * @return the stored file
     */
    public static File storeArtifactTemp(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return storeArtifactTemp(file.getOriginalFilename(), inputStream);
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store mule artifact. Please try again!", ex);
        }
    }

    /**
     * Save artifact as a temporary file or return underlying file if already on disk.
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
            try (InputStream inputStream = resource.getInputStream()) {
                return storeArtifactTemp(resource.getFilename(), inputStream);
            }
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store mule artifact. Please try again!", ex);
        }
    }

    /**
     * Save artifact as a temporary file.
     *
     * @param name
     *     the file name
     * @param inputStream
     *     the input stream of the file
     * @return the stored file
     */
    public static File storeArtifactTemp(String name, InputStream inputStream) {
        String fileName = StringUtils.cleanPath(name);

        if (!StringUtils.hasText(fileName) || fileName.contains("..")) {
            throw new DeployArtifactException("You must provide a valid and safe artifact file name: " + fileName);
        }

        try {
            Path tempPath = Files.createTempDirectory("mule_artifact");
            File aFile = new File(tempPath.toFile(), fileName);
            Files.copy(inputStream, aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Created temporal file for '{}' at {}", fileName, aFile.getAbsolutePath());
            }

            return aFile;
        } catch (FileAlreadyExistsException fae) {
            throw new DeployArtifactException("File already exists: " + fileName, fae);
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store artifact file " + fileName + ". Please try again!", ex);
        }
    }

    /**
     * Clean up folder by deleting its contents.
     *
     * @param folder
     *     the folder to delete
     */
    public static void cleanUpFolder(File folder) {
        try {
            if (folder != null && folder.exists()) {
                deleteDirectory(folder);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to fully clean up folder. Error: {}", e.getMessage(), e);
        }
    }

}
