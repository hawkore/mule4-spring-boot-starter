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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkore.springframework.boot.mule.controller.MuleRuntimeDeploymentServices;
import org.hawkore.springframework.boot.mule.controller.dto.ErrorMessage;
import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.hawkore.springframework.boot.mule.test.ut.AbstractSpringTest;
import org.hawkore.springframework.boot.mule.utils.StorageUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Just test calling deployment services services on a stopped Mule Runtime Container
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@TestPropertySource({"classpath:application-test.properties", "classpath:application-test-patches.properties"})
@SpringBootTest(classes = {SpringBootEmbeddedMuleRuntimeApp.class})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class SpringBootMule4ShutdownTests extends AbstractSpringTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4ShutdownTests.class);
    private static final String ENDPOINT_CONTEXT = "/mule";
    private static final String ENDPOINT_APPLICATIONS = "/applications";
    private static final String ENDPOINT_DOMAINS = "/domains";
    private MockMvc mockMvc;
    @Autowired
    private MuleRuntimeDeploymentServices deploymentServices;
    @Autowired
    private SpringMuleContainerV1Impl container;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;
    @Autowired
    private MuleRuntimeHealthIndicator muleRuntimeHealthIndicator;

    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(deploymentServices).setMessageConverters(jacksonMessageConverter)
                           .build();
    }

    @Test
    public void muleAplicationListKO() throws Exception {
        // ensure stopped Mule container to cause error when consuming deployment services
        container.stop();
        // list applications
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();

        // deserialize response
        ErrorMessage error = objectMapper.readValue(list.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleDomainListsKO() throws Exception {
        // ensure stopped Mule container to cause error when consuming deployment services
        container.stop();

        // list domains
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();

        // deserialize response
        ErrorMessage error = objectMapper.readValue(list.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleHealthIndicatorDOWN() {
        // ensure stopped Mule container to cause error when consuming deployment services
        container.stop();
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        Health health = muleRuntimeHealthIndicator.health();
        Assert.assertEquals(Status.DOWN, health.getStatus());
    }

    @Test(expected = DeployArtifactException.class)
    public void deployArtifactNullTask() {
        container.deployArtifact(null, null, null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void executeWithinClassLoaderNulls() {
        container.executeWithinClassLoader(null, null);
    }

    @Test
    public void executeWithinClassLoaderNullCLOK() {
        Exception ex = null;
        try {
            container.executeWithinClassLoader(null, () -> LOGGER.debug("Runnable task"));
        } catch (Exception e) {
            ex = e;
            LOGGER.error("An exception occurs", e);
        }
        Assert.assertNull("An exception occurs", ex);
    }

    @Test(expected = IllegalStateException.class)
    public void executeWithinClassLoaderNullsRunnable() {
        container.executeWithinClassLoader(this.getClass().getClassLoader(), null);
    }

    @Test
    public void executeWithinClassLoaderOK() {
        Exception ex = null;
        try {
            container.executeWithinClassLoader(this.getClass().getClassLoader(), () -> LOGGER.debug("Runable task"));
        } catch (Exception e) {
            ex = e;
            LOGGER.error("An exception occurs", e);
        }
        Assert.assertNull("An exception occurs", ex);
    }

    @Test
    public void cleanUpFolder() {
        Exception ex = null;
        try {
            // should not fail, just log wanning
            StorageUtils.cleanUpFolder(null);
        } catch (Exception e) {
            ex = e;
            LOGGER.error("An exception occurs", e);
        }
        Assert.assertNull("An exception occurs", ex);
    }

}
