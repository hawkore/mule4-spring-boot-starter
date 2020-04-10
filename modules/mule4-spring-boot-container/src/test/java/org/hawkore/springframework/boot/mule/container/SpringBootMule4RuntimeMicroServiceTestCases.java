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

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkore.springframework.boot.mule.config.MuleConfigProperties;
import org.hawkore.springframework.boot.mule.controller.MuleRuntimeDeploymentServices;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.hawkore.springframework.boot.mule.test.ut.AbstractSpringTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
@SpringBootTest(classes = {MuleConfigProperties.class, SpringBootEmbeddedMuleRuntimeApp.class})
public class SpringBootMule4RuntimeMicroServiceTestCases extends AbstractSpringTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4RuntimeMicroServiceTestCases.class);
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

    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(deploymentServices).setMessageConverters(jacksonMessageConverter)
                           .build();
    }

    @After
    public void after() {

    }

    @Test
    public void muleAplicationList() throws Exception {

        // list applications
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Application> apps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must not be empty", !apps.isEmpty());

        apps.forEach(a -> LOGGER.debug("Deployed app {}", a));
    }

    @Test
    public void muleDomainLists() throws Exception {

        // list applications
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Domain> domains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        domains.forEach(a -> LOGGER.debug("Deployed domain {}", a));
    }

}
