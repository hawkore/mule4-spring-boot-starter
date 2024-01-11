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
package org.hawkore.springframework.boot.mule.container.v2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkore.springframework.boot.mule.controller.MuleRuntimeDeploymentServices;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Artifact;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.controller.dto.ErrorMessage;
import org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator;
import org.hawkore.springframework.boot.mule.test.main.SpringBootEmbeddedMuleRuntimeApp;
import org.hawkore.springframework.boot.mule.test.ut.AbstractSpringTest;
import org.hawkore.springframework.boot.mule.utils.ClassLoaderStrategy;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader.DefaultStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.deployment.model.api.application.ApplicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
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
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainFolder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@TestPropertySource({"classpath:application-test.properties"})
@SpringBootTest(classes = {SpringBootEmbeddedMuleRuntimeApp.class})
public class SpringBootMule4RuntimeTests extends AbstractSpringTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootMule4RuntimeTests.class);
    private static final String ENDPOINT_CONTEXT = "/mule";
    private static final String ENDPOINT_APPLICATIONS = "/applications";
    private static final String ENDPOINT_DOMAINS = "/domains";
    // sample mule artifacts
    private static final String TEST_APP_NAME = "test-mule-app-1.0.0-mule-application";
    private static final String TEST_DOMAIN_NAME = "test-mule-domain-1.0.0-mule-domain";
    private static final String TEST_APP_LOCATION
        = "../test-resources/artifacts/test-mule-app/target/test-mule-app-1.0.0-mule-application.jar";
    private static final String TEST_DOMAIN_LOCATION
        = "../test-resources/artifacts/test-mule-domain/target/test-mule-domain-1.0.0-mule-domain.jar";
    // sample mule artifacts with errors
    private static final String TEST_BAD_APP_NAME = "test-mule-app-bad-1.0.0-mule-application";
    private static final String TEST_BAD_DOMAIN_NAME = "test-mule-domain-bad-1.0.0-mule-domain";
    private static final String TEST_BAD_APP_LOCATION
        = "../test-resources/artifacts/test-mule-app-bad/target/test-mule-app-bad-1.0.0-mule-application.jar";
    private static final String TEST_BAD_DOMAIN_LOCATION
        = "../test-resources/artifacts/test-mule-domain-bad/target/test-mule-domain-bad-1.0.0-mule-domain.jar";
    private MockMvc mockMvc;
    @Autowired
    private MuleRuntimeDeploymentServices deploymentServices;
    @Autowired
    private SpringMuleContainerV2Impl container;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;
    @Autowired
    private MuleRuntimeHealthIndicator muleRuntimeHealthIndicator;
    @Autowired
    private ApplicationContext applicationContext;

    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(deploymentServices).setMessageConverters(jacksonMessageConverter)
                           .build();
    }

    @Test
    public void muleAplicationLifeCycleTests() throws Exception {

        // deploy application
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(createMultipartFile("file", TEST_APP_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                               .andReturn();

        Assert.assertTrue("Application should be deployed!!", container.isApplicationDeployed(TEST_APP_NAME));

        // coverage started
        container.onApplicationEvent(new ContextRefreshedEvent(applicationContext));

        // deserialize response
        List<Application> apps = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertFalse("List of applications must not be empty", apps.isEmpty());

        // list applications
        MvcResult list = mockMvc.perform(
                MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        apps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertFalse("List of applications must not be empty", apps.isEmpty());

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

        Assert.assertFalse(container.isApplicationDeployed("INEXISTENT"));
        Assert.assertFalse(container.isApplicationInstalled("INEXISTENT"));
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
        domains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        // undeploy domain
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        // deserialize response
        domains = objectMapper.readValue(undeploy.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        // size 1 - mule default domain
        Assert.assertEquals("List of domains must be 1 - default domain", 1, domains.size());

        domains.forEach(a -> LOGGER.debug("Deployed domain {}", a));

        Assert.assertFalse(container.isDomainDeployed("INEXISTENT"));
        Assert.assertFalse(container.isDomainInstalled("INEXISTENT"));
    }

    @Test
    public void muleDeployApplicationKO_INVALID_APP_FILE_NAME() throws Exception {
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
    public void muleDeployApplicationKO_INVALID_DESIGNED_APP() throws Exception {
        // deploy application with errors
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(createMultipartFile("file", TEST_BAD_APP_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertTrue(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        // undeploy application
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", TEST_BAD_APP_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        Assert.assertEquals(Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleDeployDomainKO_INVALID_DOMAIN_FILE_NAME() throws Exception {
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
    public void muleDeployDomainKO_INVALID_DESIGNED_DOMAIN() throws Exception {
        // deploy domain with errors
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS)
                                               .file(createMultipartFile("file", TEST_BAD_DOMAIN_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertTrue(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        // undeploy domain
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_BAD_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        Assert.assertEquals("Not expected status!", Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleUnDeployDomainKO_DOMAIN_NOT_EXISTS() throws Exception {

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
    public void muleUnDeployApplicationKO_APP_NOT_EXISTS() throws Exception {

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
    public void muleDeployDomainKO_DIRECT_DOMAIN_FOLDER() throws Exception {

        // create a folfer into domains folder

        getDomainFolder(TEST_BAD_DOMAIN_NAME).mkdirs();

        Assert.assertTrue(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_BAD_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        Assert.assertEquals(Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleDeployApplicationKO_DIRECT_APP_FOLDER() throws Exception {

        // create a folfer into apps folder

        getAppFolder(TEST_BAD_APP_NAME).mkdirs();

        Assert.assertTrue(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", TEST_BAD_APP_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        Assert.assertEquals(Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleHealthIndicatorOUT_OF_SERVICE_BY_DOMAIN() throws Exception {
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        // deploy domain with errors
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS)
                                               .file(createMultipartFile("file", TEST_BAD_DOMAIN_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertEquals(Status.OUT_OF_SERVICE, muleRuntimeHealthIndicator.health().getStatus());

        // list domains
        MvcResult list = mockMvc.perform(
                MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Domain> domains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        Assert.assertTrue("List of domains must be greater than 1", domains.size() > 1);

        // undeploy domain
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_BAD_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        domains.forEach(a -> LOGGER.debug("Deployed domain {}", a));

        Assert.assertFalse(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        Assert.assertEquals("Not expected status!", Status.UP, muleRuntimeHealthIndicator.health().getStatus());

        // list domains
        list = mockMvc.perform(
                MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk()).andReturn();
        // deserialize response
        domains = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Domain>>() {});

        Assert.assertNotNull(domains);
        // size 1 - mule default domain
        Assert.assertEquals("List of domains must be 1 - default domain", 1, domains.size());
    }

    @Test
    public void muleHealthIndicatorOUT_OF_SERVICE_BY_APP() throws Exception {
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        // deploy application with errors
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(createMultipartFile("file", TEST_BAD_APP_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertEquals("Not expected status! ", Status.OUT_OF_SERVICE,
            muleRuntimeHealthIndicator.health().getStatus());

        // list applications
        MvcResult list = mockMvc.perform(
                MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk()).andReturn();
        // deserialize response
        List<Application> apps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertFalse("List of applications must not be empty", apps.isEmpty());

        apps.forEach(a -> LOGGER.debug("Deployed app {}", a));

        // undeploy application
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", TEST_BAD_APP_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        Assert.assertEquals("Not expected status! ", Status.UP, muleRuntimeHealthIndicator.health().getStatus());

        // list applications
        list = mockMvc.perform(
                MockMvcRequestBuilders.get(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).accept(MediaType.APPLICATION_JSON))
                   .andExpect(status().isOk()).andReturn();
        // deserialize response
        apps = objectMapper.readValue(list.getResponse().getContentAsByteArray(),
            new TypeReference<List<Application>>() {});

        Assert.assertNotNull(apps);
        Assert.assertTrue("List of applications must be empty", apps.isEmpty());
    }

    @Test
    public void muleHealthIndicatorOUT_OF_SERVICE_BY_APP_AND_DOMAIN() throws Exception {
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        // deploy application with errors
        MvcResult deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS)
                                               .file(createMultipartFile("file", TEST_BAD_APP_LOCATION))
                                               .accept(MediaType.APPLICATION_JSON))
                               .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        ErrorMessage error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertEquals("Not expected status! ", Status.OUT_OF_SERVICE,
            muleRuntimeHealthIndicator.health().getStatus());

        Assert.assertNotNull(muleRuntimeHealthIndicator);

        // deploy domain with errors
        deploy = mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS)
                                     .file(createMultipartFile("file", TEST_BAD_DOMAIN_LOCATION))
                                     .accept(MediaType.APPLICATION_JSON))
                     .andExpect(status().is(HttpStatus.INTERNAL_SERVER_ERROR.value())).andReturn();
        // deserialize response
        error = objectMapper.readValue(deploy.getResponse().getContentAsByteArray(), ErrorMessage.class);

        Assert.assertNotNull(error);

        LOGGER.debug("Error message {}", error);

        Assert.assertEquals("Not expected status! ", Status.OUT_OF_SERVICE,
            muleRuntimeHealthIndicator.health().getStatus());

        // undeploy domain
        MvcResult undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_DOMAINS).param("name", TEST_BAD_DOMAIN_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isDomainInstalled(TEST_BAD_DOMAIN_NAME));
        Assert.assertFalse(container.isDomainDeployed(TEST_BAD_DOMAIN_NAME));

        // undeploy application
        undeploy = mockMvc.perform(
            MockMvcRequestBuilders.delete(ENDPOINT_CONTEXT + ENDPOINT_APPLICATIONS).param("name", TEST_BAD_APP_NAME)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        Assert.assertFalse(container.isApplicationInstalled(TEST_BAD_APP_NAME));
        Assert.assertFalse(container.isApplicationDeployed(TEST_BAD_APP_NAME));

        Assert.assertEquals("Not expected status! ", Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleHealthIndicatorDOWN() throws Exception {
        container.stop();
        Assert.assertNotNull(muleRuntimeHealthIndicator);
        Health health = muleRuntimeHealthIndicator.health();
        Assert.assertEquals(Status.DOWN, health.getStatus());
        container.start();
        // waits until status UP
        Assert.assertEquals("Not expected status! ", Status.UP, muleRuntimeHealthIndicator.health().getStatus());
    }

    @Test
    public void muleContainerClassLoader() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        Assert.assertNull(container.containerClassLoader().getResource("unexistent"));
        Assert.assertFalse(container.containerClassLoader().getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(container.containerClassLoader().getResource("application-test.properties"));
        Assert.assertTrue(
            container.containerClassLoader().getResources("application-test.properties").hasMoreElements());
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
    public void muleCompositeClassLoaderWithStrategyChilds() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(),
            new DefaultStrategy(s -> true, s -> false, s -> true, s -> false), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
    }

    @Test
    public void muleCompositeClassLoaderWithStrategyParentAllExcludedChildSearch()
        throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(new CompositeClassLoader(container.containerClassLoader(),
            new DefaultStrategy(s -> false, s -> true, s -> false, s -> true)),
            new DefaultStrategy(s -> true, s -> false, s -> true, s -> false), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
        Assert.assertNotNull(cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"));
    }

    @Test
    public void muleCompositeClassLoaderWithStrategyParentEmptyWithChildInclusionResourceSearch()
        throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(new URLClassLoader(new URL[0]),
            new DefaultStrategy(s -> true, s -> false, s -> getName(s).startsWith("application"),
                s -> !getName(s).endsWith(".properties")), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("application"));
        Assert.assertFalse(cl.getResources("application").hasMoreElements());
        Assert.assertNull(cl.getResource("application.properties"));
        Assert.assertFalse(cl.getResources("application.properties").hasMoreElements());
        Assert.assertNotNull(cl.getResource("application-test.properties"));
        Assert.assertTrue(cl.getResources("application-test.properties").hasMoreElements());
        Assert.assertNotNull(cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"));
    }

    @Test
    public void muleCompositeClassLoaderWithStrategyParentChildAllExcludedSearch()
        throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(new CompositeClassLoader(container.containerClassLoader(),
            new DefaultStrategy(s -> false, s -> true, s -> false, s -> true)),
            new DefaultStrategy(s -> false, s -> true, s -> true, s -> true), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNull(cl.getResource("application-test.properties"));
        Assert.assertFalse(cl.getResources("application-test.properties").hasMoreElements());
        // must throw ClassNotFoundException
        try {
            cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
    }

    @Test
    public void muleCompositeClassLoaderWithStrategyChildsAllExcluded() throws IOException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(),
            new DefaultStrategy(s -> false, s -> true, s -> false, s -> true), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNull(cl.getResource("application-test.properties"));
        Assert.assertFalse(cl.getResources("application-test.properties").hasMoreElements());
    }

    @Test
    public void muleCompositeClassLoaderWithStrategyChildsAllExcludedClasses()
        throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(),
            new DefaultStrategy(s -> false, s -> true, s -> false, s -> true), this.getClass().getClassLoader());
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNull(cl.getResource("application-test.properties"));
        Assert.assertFalse(cl.getResources("application-test.properties").hasMoreElements());
        // must throw ClassNotFoundException
        try {
            cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
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

    @Test
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
        try {
            cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
    }

    @Test
    public void muleCompositeClassLoaderRuntimeExceptionInclusionFilter() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        CompositeClassLoader cl = new CompositeClassLoader(container.containerClassLoader(), null, null,
            n -> {throw new RuntimeException();}, null, this.getClass().getClassLoader());
        Assert.assertNotNull(cl.loadClass("org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator"));
        Assert.assertNull(cl.getResource("unexistent"));
        Assert.assertFalse(cl.getResources("unexistent").hasMoreElements());
        Assert.assertNull(cl.getResource("application-test.properties"));
        Assert.assertFalse(cl.getResources("application-test.properties").hasMoreElements());
    }

    public void classClassLoaderOrgMuleServices() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        // must throw ClassNotFoundException
        container.containerClassLoader().loadClass("org.mule.service.http.impl.service.HttpServiceImplementation");
    }

    @Test
    public void muleCompositeClassLoaderOrgMuleServices() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        // this class is in class loader
        Assert.assertNotNull(
            this.getClass().getClassLoader().loadClass("org.mule.service.http.impl.service.HttpServiceImplementation"));
        // must throw ClassNotFoundException as org.mule.service.** are filtered
        try {
            container.containerClassLoader().loadClass("org.mule.service.http.impl.service.HttpServiceImplementation");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
    }

    @Test
    public void muleCompositeClassLoaderComMuleServices() throws IOException, ClassNotFoundException {
        Assert.assertNotNull(container.containerClassLoader());
        // must throw ClassNotFoundException as com.muleloft.service.** are filtered
        try {
            container.containerClassLoader().loadClass("org.mule.service.http.impl.service.HttpServiceImplementation");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
    }

    @Test
    public void registerSpringBootJarHandler()
        throws IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException,
                   IllegalAccessException, InvocationTargetException {
        ClassLoader filteredClassLoader = new CompositeClassLoader(this.getClass().getClassLoader(), null,
            s -> s.equals("org.springframework.boot.loader.jar.Handler"), null, null);
        // simulate executable jar (just in classpath)
        container.registerSpringBootJarHandler(null);
        // simulate not executable jar
        container.registerSpringBootJarHandler(filteredClassLoader);
        // checks filteredClassLoader works -> ClassNotFoundException
        try {
            filteredClassLoader.loadClass("org.springframework.boot.loader.jar.Handler");
            Assert.fail("Expected an ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
        }
    }

    @Test
    public void muleArtifacts() throws IOException, ClassNotFoundException {
        // Apps
        Artifact<Application> app = new Application().setName("app1").setLastModified(0)
                                        .setStatus(ApplicationStatus.STARTED);
        Artifact<Application> app3 = new Application();
        Assert.assertEquals(app, app3.setName("app1").setLastModified(0).setStatus(ApplicationStatus.STARTED));
        Assert.assertEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, app3.setName("appN").setLastModified(0).setStatus(ApplicationStatus.STARTED));
        Assert.assertNotEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, app3.setName("appN").setLastModified(1).setStatus(ApplicationStatus.STARTED));
        Assert.assertNotEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, app3.setName("appN").setLastModified(0).setStatus(ApplicationStatus.INITIALISED));
        Assert.assertNotEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, app3.setName("app1").setLastModified(1).setStatus(ApplicationStatus.STARTED));
        Assert.assertNotEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, app3.setName("app1").setLastModified(0).setStatus(ApplicationStatus.INITIALISED));
        Assert.assertNotEquals(app.hashCode(), app3.hashCode());
        Assert.assertNotEquals(app, new Object());
        // Domains
        Artifact<Domain> domain = new Domain().setName("app1").setLastModified(0).setStatus(ApplicationStatus.STARTED);
        Assert.assertNotEquals(app.hashCode(), domain.hashCode());
        Artifact<Domain> domain2 = new Domain().setName("app1").setLastModified(0).setStatus(ApplicationStatus.STARTED);
        Assert.assertEquals(domain, domain2);
        Assert.assertEquals(domain.hashCode(), domain2.hashCode());
        Assert.assertNotEquals(domain, domain2.setName("appN").setLastModified(0).setStatus(ApplicationStatus.STARTED));
        Assert.assertNotEquals(domain.hashCode(), domain2.hashCode());
        Assert.assertNotEquals(domain, new Object());

        boolean codeCoverageAppEquals = app.equals(app);
        Assert.assertTrue(codeCoverageAppEquals);
        boolean codeCoverageDomainEquals = domain.equals(domain);
        Assert.assertTrue(codeCoverageDomainEquals);
        boolean codeCoverageArtifactEquals = domain.equals(app);
        Assert.assertFalse(codeCoverageArtifactEquals);
    }

    private MockMultipartFile createMultipartFile(String paramName, String fileLocation) throws IOException {
        File f = new File(fileLocation);
        return new MockMultipartFile(paramName, f.getName(), (String)null, (byte[])FileCopyUtils.copyToByteArray(f));
    }

}
