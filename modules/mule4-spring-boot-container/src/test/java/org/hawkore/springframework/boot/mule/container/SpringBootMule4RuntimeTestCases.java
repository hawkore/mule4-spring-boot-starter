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

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkore.springframework.boot.mule.config.MuleConfigProperties;
import org.hawkore.springframework.boot.mule.controller.MuleRuntimeDeploymentServices;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.controller.dto.ErrorMessage;
import org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.hawkore.springframework.boot.mule.test.ut.AbstractSpringTest;
import org.hawkore.springframework.boot.mule.utils.ClassLoaderStrategy;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader;
import org.junit.After;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileCopyUtils;

import static org.apache.commons.io.FilenameUtils.getName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@TestPropertySource({"classpath:application-test.properties"})
@SpringBootTest(classes = {MuleConfigProperties.class, SpringBootEmbeddedMuleRuntimeApp.class})
public class SpringBootMule4RuntimeTestCases extends AbstractSpringTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4RuntimeTestCases.class);
    private static final String ENDPOINT_CONTEXT = "/mule";
    private static final String ENDPOINT_APPLICATIONS = "/applications";
    private static final String ENDPOINT_DOMAINS = "/domains";
    private static final String TEST_APP_NAME = "test-mule-app-1.0.0-mule-application";
    private static final String TEST_DOMAIN_NAME = "test-mule-domain-1.0.0-mule-domain";
    private static final String TEST_APP_LOCATION
        = "../test-resources/artifacts/test-mule-app/target/test-mule-app-1.0.0-mule-application.jar";
    private static final String TEST_DOMAIN_LOCATION
        = "../test-resources/artifacts/test-mule-domain/target/test-mule-domain-1.0.0-mule-domain.jar";
    private MockMvc mockMvc;
    @Autowired
    private MuleRuntimeDeploymentServices deploymentServices;
    @Autowired
    private SpringMuleContainerImpl container;
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

    @After
    public void after() {

    }

    @Test
    public void muleAplicationLifeCycleTests() throws Exception {

        // deploy application
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(createMultipartFile("file", TEST_APP_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                               .andReturn();

        Assert.assertTrue("Application should be deployed!!", container.isApplicationDeployed(TEST_APP_NAME));

        // deserialize response
        List<Application> apps = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must not be empty", !apps.isEmpty());

        // list applications
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        apps = objectMapper
                   .readValue(list.getResponse().getContentAsByteArray(), new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must not be empty", !apps.isEmpty());

        apps.forEach(a -> LOGGER.debug("Deployed app {}", a));

        // undeploy application
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", TEST_APP_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        // deserialize response
        apps = objectMapper.readValue(undeploy.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must be empty", apps.isEmpty());
    }

    @Test
    public void muleDomainLifeCycleTests() throws Exception {
        // deploy domain
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS)
                                               .file(createMultipartFile("file", TEST_DOMAIN_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                               .andReturn();

        Assert.assertTrue("Domain should be deployed!!", container.isDomainDeployed(TEST_DOMAIN_NAME));
        // deserialize response
        List<Domain> domains = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        // list domains
        MvcResult list = mockMvc.perform(
            MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        domains = objectMapper
                      .readValue(list.getResponse().getContentAsByteArray(), new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        // undeploy domain
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        // deserialize response
        domains = objectMapper
                      .readValue(undeploy.getResponse().getContentAsByteArray(), new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        // size 1 - mule default domain
        Assert.assertTrue("List of domains must be 1 - default domain", domains.size() == 1);

        domains.forEach(a -> LOGGER.debug("Deployed domain {}", a));
    }

    @Test
    public void muleDeployApplicationKO() throws Exception {
        // deploy application with bad original file name
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(new MockMultipartFile("file", new byte[0]))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleDeployDomainKO() throws Exception {
        // deploy domain with bad original file name
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS)
                                               .file(new MockMultipartFile("file", new byte[0]))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleUnDeployDomainKO() throws Exception {

        // un-deploy domain that not exists
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", "no-domain")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                                 .andReturn();

        // deserialize response
        ErrorMessage error = objectMapper.readValue(undeploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleUnDeployApplicationnKO() throws Exception {

        // un-deploy application that not exists
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", "no-app")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                                 .andReturn();

        // deserialize response
        ErrorMessage error = objectMapper.readValue(undeploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);
    }

    @Test
    public void muleHealthIndicatorUP() {
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        Health health = muleRuntimeHealthIndicator.health();
        Assert.assertTrue(health.getStatus().equals(Status.UP));
    }

    @Test
    public void muleContainerClassLoader() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        Assert.assertNull(container.containerClassLoader().getResource("unexistent"));
        Assert.assertFalse(container.containerClassLoader().getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(container.containerClassLoader().getResource("application-test.properties"));
        Assert
            .assertTrue(container.containerClassLoader().getResources("application-test.properties").hasMoreElements());
    }

    @Test
    public void muleCompositeClassLoaderNullChilds() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(), null, null);
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
    }

    @Test
    public void muleCompositeClassLoaderWithChilds() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(), (ClassLoaderStrategy)null,
            this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
    }

    @Test
    public void muleCompositeClassLoaderInclusionFilter() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(),
            n -> n.equals("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"), null,
            n -> getName(n).equals("application-test.properties"), null, this.getClass().getClassLoader());
        Assert.assertNotNull(cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"));
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
    }

    @Test(expected = ClassNotFoundException.class)
    public void muleCompositeClassLoaderExclusionFilter() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(), null,
            n -> n.equals("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"), null,
            n -> getName(n).equals("application-test.properties"), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNull(cl.getResource("application-test.properties"));
        Assert.assertFalse(cl.getResources("application-test.properties").hasMoreElements());
        // must throw ClassNotFoundException
        cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator");
    }

    private MockMultipartFile createMultipartFile(String paramName, String fileLocation) throws IOException {
        File f = new File(fileLocation);
        return new MockMultipartFile(paramName, f.getName(), (String)null, (byte[])FileCopyUtils.copyToByteArray(f));
    }

}
