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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkore.springframework.boot.mule.config.MuleContainerConfiguration;
import org.hawkore.springframework.boot.mule.controller.MuleRuntimeDeploymentServices;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.hawkore.springframework.boot.mule.test.ut.AbstractSpringTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Starts Mule Runtime and apps/domains provided as properties, see application-test-deploy.properties
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@TestPropertySource({"classpath:application-test.properties", "classpath:application-test-deploy.properties"})
@SpringBootTest(classes = {SpringBootEmbeddedMuleRuntimeApp.class})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class SpringBootMule4RuntimeStopStartCleanStartupTests extends AbstractSpringTest {

    private static final Logger LOGGER = LoggerFactory
                                             .getLogger(SpringBootMule4RuntimeStopStartCleanStartupTests.class);
    private static final String ENDPOINT_CONTEXT = "/mule";
    private static final String ENDPOINT_APPLICATIONS = "/applications";
    private static final String ENDPOINT_DOMAINS = "/domains";
    private MockMvc mockMvc;
    @Autowired
    private MuleRuntimeDeploymentServices deploymentServices;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;
    private Logger logger;
    @Autowired
    private MuleContainerConfiguration containerConfiguration;
    @Autowired
    private SpringMuleContainerV1Impl container;
    @Autowired
    private MuleRuntimeHealthIndicator muleRuntimeHealthIndicator;

    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(deploymentServices).setMessageConverters(jacksonMessageConverter)
                           .build();
    }

    @Test
    public void muleStopStartCleanStartUp() throws Exception {
        // list domains
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Domain> initialDomains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(initialDomains);
        Assert.assertTrue("List of domains must be greater than 1", initialDomains.size() > 1);

        initialDomains.forEach(a -> LOGGER.debug("Deployed domain {}", a));

        // remove default domain as it is not deployed by this test
        initialDomains = initialDomains.stream().filter(s -> !s.getName().equals("default"))
                             .collect(Collectors.toList());

        // list applications
        list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk()).andReturn();

        // deserialize response
        List<Application> initialApps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        // restart Mule container to test whether applications/domains are not re-deployed
        container.stop();
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        Health health = muleRuntimeHealthIndicator.health();
        Assert.assertEquals(Status.DOWN, health.getStatus());
        Assert.assertFalse(container.isRunning());
        // sent start signal
        container.start();
        //  status UP
        Assert.assertEquals(Status.UP, muleRuntimeHealthIndicator.health().getStatus());
        Assert.assertTrue(container.isRunning());
        // list domains
        list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Domain> domains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        domains.forEach(a -> LOGGER.debug("Deployed domain {}", a));

        // remove default domain as it is not deployed by this test
        domains = domains.stream().filter(s -> !s.getName().equals("default")).collect(Collectors.toList());

        // list applications
        list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Application> apps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must not be empty", !apps.isEmpty());

        apps.forEach(a -> LOGGER.debug("Deployed app {}", a));

        Assert.assertFalse("Domains are NOT been re-deployed!", Objects.deepEquals(initialDomains, domains));
        Assert.assertFalse("Applications are NOT been re-deployed!", Objects.deepEquals(initialApps, apps));
    }

}
