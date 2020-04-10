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
import java.io.IOException;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * StorageUtilsTest
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class StorageUtilsTest {

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

}
