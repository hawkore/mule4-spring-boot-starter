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
package org.hawkore.springframework.boot.mule.container;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test fail startup on invalid configuration, bad application or bad domain
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class SpringBootMule4FailStartupTestCases {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4FailStartupTestCases.class);

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithBadApplication() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,classpath:application-test-deploy-bad-app"
                + ".properties"});
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithBadDomain() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-bad-domain.properties"});
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithNOTEXISTSApplication() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-notfound-app" + ".properties"});
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithNOTEXISTSDomain() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-notfound-domain.properties"});
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithNOTEXISTSClasspathApplication() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-notfound-classpath-app" + ".properties"});
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithNOTEXISTSClasspathDomain() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-notfound-classpath-domain.properties"});
    }

    @Test(expected = IllegalStateException.class)
    public void expectedFailWithIlegalState() throws Exception {
        SpringBootEmbeddedMuleRuntimeApp.main(new String[] {});
    }

}
