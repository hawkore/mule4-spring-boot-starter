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
package org.hawkore.springframework.boot.mule.container.v1;

import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Test fail startup on invalid configuration, bad application or bad domain
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class SpringBootMule4FailStartup1Tests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4FailStartup1Tests.class);
    private ConfigurableApplicationContext runningContext;

    @After
    public void after() throws Exception {
        // ensures previous app context is closed before run next one
        if (runningContext != null) {
            LOGGER.debug("Closing SpringBootAppContext after test...");
            runningContext.close();
            runningContext = null;
        }
    }

    @Test(expected = DeployArtifactException.class)
    public void expectedFailWithBadApplication() throws Exception {
        runningContext = SpringBootEmbeddedMuleRuntimeApp.start(new String[] {
            "--spring.config.location=classpath:application-test.properties,"
                + "classpath:application-test-deploy-bad-app.properties"});
    }

}
