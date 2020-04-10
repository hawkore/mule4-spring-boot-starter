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
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.core.api.util.compression.InvalidZipFileException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.apache.commons.io.IOUtils.copy;

/**
 * Storage utils.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class StorageUtils {

    private StorageUtils() {}

    /**
     * Unzip.
     *
     * @param archive
     *     the archive
     * @param directory
     *     the directory
     * @param verify
     *     the verify
     * @throws IOException
     *     the io exception
     */
    public static void unzip(InputStream archive, File directory, boolean verify) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Provided directory is not a directory: " + directory);
            }
        } else {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory: " + directory);
            }
        }
        try (ZipInputStream zip = new ZipInputStream(archive)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (verify) {
                    verifyZipFilePaths(entry);
                }
                File f = FileUtils.newFile(directory, entry.getName());
                if (entry.isDirectory()) {
                    if (!f.exists() && !f.mkdirs()) {
                        throw new IOException("Could not create directory: " + f);
                    }
                } else {
                    File file = new File(directory, entry.getName());
                    if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                        throw new IOException("Unable to create folders for zip entry: " + entry.getName());
                    }

                    OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                    copy(zip, os);
                    IOUtils.closeQuietly(os);
                }
            }
        }
    }

    private static void verifyZipFilePaths(ZipEntry entry) throws InvalidZipFileException {
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
     * Store artifact temp file.
     *
     * @param file
     *     the file
     * @return the file
     */
    public static File storeArtifactTemp(MultipartFile file) {
        try {
            return storeArtifactTemp(file.getOriginalFilename(), file.getInputStream());
        } catch (DeployArtifactException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store mule artifact. Please try again!", ex);
        }
    }

    /**
     * Store artifact file.
     *
     * @param name
     *     the name
     * @param inputStream
     *     the input stream
     * @return the file
     */
    public static File storeArtifactTemp(String name, InputStream inputStream) {
        String fileName = StringUtils.cleanPath(name);
        if (StringUtils.isEmpty(name) || name.isEmpty()) {
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
            return aFile;
        } catch (Exception ex) {
            throw new DeployArtifactException("Could not store artifact file " + fileName + ". Please try again!", ex);
        }
    }

}
