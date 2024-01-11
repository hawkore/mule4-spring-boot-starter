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
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hawkore.springframework.boot.mule.config.MuleConfigProperties;
import org.hawkore.springframework.boot.mule.container.SpringMuleContainerImpl;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.mule.runtime.deployment.model.api.application.ApplicationStatus;
import org.mule.runtime.module.launcher.DefaultMuleContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainsFolder;

/**
 * Embedded Spring Mule Container
 * <p>
 * For org.mule.runtime.module.launcher.DefaultMuleContainer implementation since Mule version 4.5.1
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@Component("SpringMuleContainer")
@ConditionalOnClass(name = SpringMuleContainerV2Impl.MULE_CONTAINER_CLASS)
public class SpringMuleContainerV2Impl extends SpringMuleContainerImpl {

    /**
     * The Mule container class.
     */
    static final String MULE_CONTAINER_CLASS = "org.mule.runtime.module.launcher.DefaultMuleContainer";
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringMuleContainerV2Impl.class);
    @Autowired
    private MuleConfigProperties configProperties;
    private DefaultMuleContainer muleContainer;

    /**
     * Whether application is deployed and running.
     *
     * @param application
     *     the application
     * @return the boolean
     */
    @Override
    public boolean isApplicationDeployed(String application) {
        checkRunning();
        return Optional.ofNullable(muleContainer.getDeploymentService().findApplication(application))
                   .map(a -> ApplicationStatus.STARTED.equals(a.getStatus())).orElse(false);
    }

    /**
     * Whether domain is deployed and running.
     *
     * @param domain
     *     the domain
     * @return the boolean
     */
    @Override
    public boolean isDomainDeployed(String domain) {
        checkRunning();
        return Optional.ofNullable(muleContainer.getDeploymentService().findDomain(domain))
                   .map(d -> new File(getDomainsFolder(), domain + ARTIFACT_ANCHOR_SUFFIX).exists()).orElse(false);
    }

    /**
     * Gets installed applications.
     *
     * @return the applications
     */
    @Override
    public List<Application> getApplications() {
        checkRunning();
        return muleContainer.getDeploymentService().getApplications().stream()
                   .filter(f -> isApplicationInstalled(f.getArtifactName())).map(
                f -> new Application().setName(f.getArtifactName()).setStatus(f.getStatus())
                         .setLastModified(f.getLocation().lastModified())).collect(Collectors.toList());
    }

    /**
     * Gets installed domains.
     *
     * @return the domains
     */
    @Override
    public List<Domain> getDomains() {
        checkRunning();
        return muleContainer.getDeploymentService().getDomains().stream()
                   .filter(f -> isDomainInstalled(f.getArtifactName())).map(
                f -> new Domain().setName(f.getArtifactName()).setStatus(isDomainDeployed(f.getArtifactName())
                                                                             ? ApplicationStatus.STARTED
                                                                             : ApplicationStatus.DEPLOYMENT_FAILED)
                         .setLastModified(f.getLocation().lastModified())).collect(Collectors.toList());
    }

    /**
     * Undeploy application.
     *
     * @param applicationName
     *     the application name
     */
    @Override
    public synchronized void undeployApplication(String applicationName) {
        checkRunning();
        try {
            if (!isApplicationInstalled(applicationName)) {
                throw new DeployArtifactException("Application not found: " + applicationName);
            }
            muleContainer.getDeploymentService().undeploy(applicationName);
            // ensure full removal from disk
            deleteDirectory(getAppFolder(applicationName));
        } catch (Exception e) {
            throw new DeployArtifactException("Unable to un-deploy mule application: " + applicationName, e);
        }
    }

    /**
     * Undeploy domain.
     *
     * @param domainName
     *     the domain name
     */
    @Override
    public synchronized void undeployDomain(String domainName) {
        checkRunning();
        try {
            if (!isDomainInstalled(domainName)) {
                throw new DeployArtifactException("Domain not found: " + domainName);
            }
            muleContainer.getDeploymentService().undeployDomain(domainName);
            // ensure full removal from disk
            deleteDirectory(getDomainFolder(domainName));
        } catch (Exception e) {
            throw new DeployArtifactException("Unable to un-deploy mule domain: " + domainName, e);
        }
    }

    /**
     * Deploy application.
     *
     * @param uri
     *     the uri
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     */
    @Override
    public synchronized void deployApplication(URI uri,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        LOGGER.info("Deploying Mule application {} with lazyInitializationEnabled={}, xmlValidationsEnabled={}, "
                        + "lazyConnectionsEnabled={}" + " ...", uri.getPath(),
            Optional.ofNullable(lazyInitializationEnabled).orElse(configProperties.isLazyInitializationEnabled()),
            Optional.ofNullable(xmlValidationsEnabled).orElse(configProperties.isXmlValidationsEnabled()),
            Optional.ofNullable(lazyConnectionsEnabled).orElse(configProperties.isLazyConnectionsEnabled()));
        deployArtifact(deploymentProperties -> muleContainer.getDeploymentService().deploy(uri, deploymentProperties),
            lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
    }

    /**
     * Deploy domain.
     *
     * @param uri
     *     the uri
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     */
    @Override
    public synchronized void deployDomain(URI uri,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        LOGGER.info("Deploying Mule domain {} with lazyInitializationEnabled={}, xmlValidationsEnabled={}, "
                        + "lazyConnectionsEnabled={}" + " ...", uri.getPath(),
            Optional.ofNullable(lazyInitializationEnabled).orElse(configProperties.isLazyInitializationEnabled()),
            Optional.ofNullable(xmlValidationsEnabled).orElse(configProperties.isXmlValidationsEnabled()),
            Optional.ofNullable(lazyConnectionsEnabled).orElse(configProperties.isLazyConnectionsEnabled()));
        deployArtifact(
            deploymentProperties -> muleContainer.getDeploymentService().deployDomain(uri, deploymentProperties),
            lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
    }

    /**
     * Init Mule container.
     *
     * @param classLoader
     *     the class loader
     */
    @Override
    public synchronized void initMuleContainer(ClassLoader classLoader) {
        muleContainer = new DefaultMuleContainer(null);
        // Start Mule Runtime container, do not register shutdown hook since it will try to kill the JVM
        executeWithinClassLoader(classLoader, () -> muleContainer.start(false));
    }

    /**
     * Dispose Mule container.
     *
     * @param classLoader
     *     the class loader
     */
    @Override
    public synchronized void disposeMuleContainer(ClassLoader classLoader) {
        if (muleContainer != null) {
            executeWithinClassLoader(classLoader, () -> {
                muleContainer.stop();
                muleContainer.getContainerClassLoader().dispose();
            });
        }
    }

}
