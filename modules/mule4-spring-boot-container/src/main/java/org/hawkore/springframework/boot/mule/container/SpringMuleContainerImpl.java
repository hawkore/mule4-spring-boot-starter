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
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandler;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hawkore.springframework.boot.mule.config.MuleConfigProperties;
import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader;
import org.hawkore.springframework.boot.mule.utils.StorageUtils;
import org.mule.runtime.api.util.MuleSystemProperties;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.deployment.model.api.application.ApplicationStatus;
import org.mule.runtime.module.artifact.api.classloader.net.MuleUrlStreamHandlerFactory;
import org.mule.runtime.module.launcher.MuleContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static java.lang.String.valueOf;
import static java.lang.System.setProperty;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FilenameUtils.getName;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppsFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getConfFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainsFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getMuleBaseFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getServerPluginsFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getServicesFolder;
import static org.mule.runtime.core.api.config.MuleDeploymentProperties.MULE_LAZY_CONNECTIONS_DEPLOYMENT_PROPERTY;
import static org.mule.runtime.core.api.config.MuleDeploymentProperties.MULE_LAZY_INIT_DEPLOYMENT_PROPERTY;
import static org.mule.runtime.core.api.config.MuleDeploymentProperties.MULE_LAZY_INIT_ENABLE_XML_VALIDATIONS_DEPLOYMENT_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;

/**
 * Embedded Spring Mule Container
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@Component("SpringMuleContainer")
@ConditionalOnClass(name = "org.mule.runtime.module.launcher.MuleContainer")
public class SpringMuleContainerImpl implements SpringMuleContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringMuleContainerImpl.class);
    /**
     * The constant ARTIFACT_ANCHOR_SUFFIX.
     */
    private static final String ARTIFACT_ANCHOR_SUFFIX = "-anchor.txt";
    /**
     * The constant LOGS_FORDER.
     */
    private static final String LOGS_FORDER = "logs";
    /**
     * whithin jar regular expression for URL termination
     */
    private static final String WITHIN_JAR_REGEX = "\\!\\/";
    /**
     * The Config properties.
     */
    @Autowired
    private MuleConfigProperties configProperties;
    // only one Mule Runtime instance is allowed within same JVM
    private static final AtomicBoolean started = new AtomicBoolean();
    private ClassLoader containerClassLoader;
    private MuleContainer muleContainer;

    /**
     * On application event.
     *
     * @param event
     *     the event
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            if (start()) {
                deployMuleArtifacts();
            }
        } else if (event instanceof ContextClosedEvent) {
            stop();
        }
    }

    /**
     * After properties set.
     *
     * @throws Exception
     *     the exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[classloader] -> {} ", this.getClass().getClassLoader().getClass().getName());
            }
            // register extension handler to access resources within packaged spring boot app
            MuleUrlStreamHandlerFactory.registerHandler("jar",
                (URLStreamHandler)ClassUtils.instantiateClass("org.springframework.boot.loader.jar.Handler"));
            MuleUrlStreamHandlerFactory.registerHandler("file",
                (URLStreamHandler)ClassUtils.instantiateClass("sun.net.www.protocol.file.Handler"));
        } catch (ClassNotFoundException e) {
            // ignored, not within packaged spring boot app
        }
    }

    /**
     * Is application deployed boolean.
     *
     * @param application
     *     the application
     * @return the boolean
     */
    @Override
    public boolean isApplicationDeployed(String application) {
        checkStopped();
        Optional<org.mule.runtime.deployment.model.api.application.Application> app = Optional.ofNullable(
            muleContainer.getDeploymentService().findApplication(application));
        return app.isPresent() && app.get().getStatus() == ApplicationStatus.STARTED && new File(getAppsFolder(),
            application + ARTIFACT_ANCHOR_SUFFIX).exists();
    }

    /**
     * Is domain deployed boolean.
     *
     * @param domain
     *     the domain
     * @return the boolean
     */
    @Override
    public boolean isDomainDeployed(String domain) {
        checkStopped();
        return Optional.ofNullable(muleContainer.getDeploymentService().findDomain(domain)).isPresent() && new File(
            getDomainsFolder(), domain + ARTIFACT_ANCHOR_SUFFIX).exists();
    }

    /**
     * Gets applications.
     *
     * @return the applications
     */
    @Override
    public List<Application> getApplications() {
        checkStopped();
        return muleContainer.getDeploymentService().getApplications().stream().map(
            f -> new Application().setName(f.getArtifactName()).setStatus(f.getStatus())
                     .setLastModified(f.getLocation().lastModified())).collect(Collectors.toList());
    }

    /**
     * Gets domains.
     *
     * @return the domains
     */
    @Override
    public List<Domain> getDomains() {
        checkStopped();
        return muleContainer.getDeploymentService().getDomains().stream().map(
            f -> new Domain().setName(f.getArtifactName()).setStatus(
                isDomainDeployed(f.getArtifactName()) ? ApplicationStatus.STARTED : ApplicationStatus.DEPLOYMENT_FAILED)
                     .setLastModified(f.getLocation().lastModified())).collect(Collectors.toList());
    }

    /**
     * Deploy application.
     *
     * @param appFile
     *     the app file
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     */
    @Override
    public synchronized void deployApplication(File appFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        checkStopped();
        deployApplication(appFile.toURI(), lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
    }

    /**
     * Undeploy application.
     *
     * @param applicationName
     *     the application name
     */
    @Override
    public void undeployApplication(String applicationName) {
        checkStopped();
        LOGGER.info("Un-deploying Mule app {} ...", applicationName);
        muleContainer.getDeploymentService().undeploy(applicationName);
    }

    /**
     * Deploy domain.
     *
     * @param domainFile
     *     the domain file
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     */
    @Override
    public synchronized void deployDomain(File domainFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        checkStopped();
        deployDomain(domainFile.toURI(), lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
    }

    /**
     * Undeploy domain.
     *
     * @param domainName
     *     the domain name
     */
    @Override
    public void undeployDomain(String domainName) {
        checkStopped();
        LOGGER.info("Un-deploying Mule domain {} ...", domainName);
        muleContainer.getDeploymentService().undeployDomain(domainName);
    }

    /**
     * Interface for running deployment tasks within the container class loader.
     */
    @FunctionalInterface
    interface DeploymentTask {

        /**
         * Deploy.
         *
         * @param deploymentProperties
         *     the deployment properties
         * @throws IOException
         *     the io exception
         */
        void deploy(Properties deploymentProperties) throws IOException;

    }

    /**
     * Interface for running tasks within the container class loader.
     */
    @FunctionalInterface
    interface ContainerTask {

        /**
         * Run.
         *
         * @throws Exception
         *     the exception
         */
        void run() throws Exception; //NOSONAR

    }

    private synchronized void deployApplication(URI uri,
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

    private synchronized void deployDomain(URI uri,
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

    synchronized boolean start() {
        try {
            if (started.getAndSet(true)) {
                return false;
            }
            LOGGER.info("Starting embedded Mule Runtime ...");
            setUpEnvironmentAndStart();
            return true;
        } catch (Exception e) {
            started.set(false);
            throw new IllegalStateException("Unable to start Mule Runtime", e);
        }
    }

    synchronized void stop() {
        if (muleContainer != null && started.getAndSet(false)) {
            LOGGER.info("Stopping embedded Mule Runtime ...");
            executeWithinClassLoader(containerClassLoader, () -> {
                muleContainer.stop();
                muleContainer.getContainerClassLoader().dispose();
            });
        }
    }

    ClassLoader containerClassLoader() {
        return containerClassLoader;
    }

    private void checkStopped() {
        if (!started.get()) {
            throw new IllegalStateException("Mule Runtime container is not running!!");
        }
    }

    private void setUpEnvironmentAndStart() {
        try {
            // this is used to signal that we are running in embedded mode.
            // Class loader model loader will not use try to use the container repository.
            setProperty("mule.mode.embedded", "true");

            // Disable log4j2 JMX MBeans since it will fail when trying to recreate the container
            setProperty("log4j2.disable.jmx", "true");

            setProperty(MULE_HOME_DIRECTORY_PROPERTY, configProperties.getBase().toURI().getPath());

            if (configProperties.isSimpleLog()) {
                setProperty(MuleSystemProperties.MULE_SIMPLE_LOG, "true");
            }

            // ensure required folders exists
            getMuleBaseFolder().mkdirs();
            getConfFolder().mkdirs();
            getLogFolder().mkdirs();

            if (configProperties.isCleanStartup()) {
                LOGGER.info("Clean-up artifact forders before run embedded Mule Runtime ...");
                cleanUpFolder(getAppsFolder());
                cleanUpFolder(getDomainsFolder());
            }

            getDomainsFolder().mkdirs();
            getDomainFolder("default").mkdirs();
            getAppsFolder().mkdirs();

            // extract Mule services as they are required to be loaded from local file system
            // we will do it always to allow update Mule runtime version on an existing mule forder
            installOrUpgradeServices();

            // extract Mule server plugins as they are required to be loaded from local file system
            // we will do it always to allow update Mule runtime version on an existing  mule forder
            installOrUpgradeServerPlugins();

            muleContainer = new MuleContainer(new String[0]);
            // create a composite classloader to avoid loading mule services from classloader
            // they will loaded from local file system by Mule Runtime
            containerClassLoader = new CompositeClassLoader(this.getClass().getClassLoader(), null,
                s -> s.startsWith("org.mule.service.") || s.startsWith("com.mulesoft.service."), null, null);

            // high priority patches classloader, patches must take precedence over rest
            ClassLoader patchesClassLoader = getPatchesClassloader();
            if (patchesClassLoader != null) {
                containerClassLoader = new CompositeClassLoader(patchesClassLoader, containerClassLoader);
            }

            // start Mule Runtime container, do not register shutdown hook since it will try to kill the JVM
            executeWithinClassLoader(containerClassLoader, () -> muleContainer.start(false));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start Mule Runtime container!!", e);
        }
    }

    // load mule services URLs from classloader and install them on local file system
    private void installOrUpgradeServices() throws IOException {
        // delete services folder to allow update mule runtime on existing base folder
        cleanUpFolder(getServicesFolder());
        getServicesFolder().mkdirs();

        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        List<URL> services = Stream.of(springClassLoader.getURLs()).filter(u -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER
                    .trace("[installOrUpgradeServices] -> dependency to filter found on classloader {} ", u.getPath());
            }
            return u.getFile().replaceAll(WITHIN_JAR_REGEX, "").endsWith("-mule-service.jar");
        }).collect(Collectors.toList());

        // extract mule services as they are required to be loaded from local file system
        // we will do it always to allow update Mule runtime version on an existing mule forder
        for (URL url : services) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Installing Mule service {} ...", url.getPath());
            }
            File destinationFile = new File(getServicesFolder(),
                getName(url.getFile().replaceAll("-mule-service\\.jar", "").replaceAll(WITHIN_JAR_REGEX, "")));
            destinationFile.mkdirs();
            StorageUtils.unzip(url.openStream(), destinationFile, true);
        }
    }

    // load mule server plugins URLs from classloader and install them on local file system
    private void installOrUpgradeServerPlugins() throws IOException {
        // delete server plugins folder to allow update mule runtime on existing base folder
        cleanUpFolder(getServerPluginsFolder());
        getServerPluginsFolder().mkdirs();

        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        List<URL> plugins = Stream.of(springClassLoader.getURLs()).filter(u -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[installOrUpgradeServerPlugins] -> dependency to filter found on classloader {} ",
                    u.getPath());
            }
            return u.getFile().replaceAll(WITHIN_JAR_REGEX, "").endsWith(".zip");
        }).collect(Collectors.toList());

        // extract server plugins as they are required to be loaded from local file system
        // we will do it always to allow update Mule runtime version on an existing mule forder
        for (URL url : plugins) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Installing Mule server plugin {} ...", url.getPath());
            }
            File destinationFile = new File(getServerPluginsFolder(),
                getName(url.getFile().replace(".zip", "").replaceAll(WITHIN_JAR_REGEX, "")));
            destinationFile.mkdirs();
            StorageUtils.unzip(url.openStream(), destinationFile, true);
        }
    }

    // load mule patches URLs from classloader
    private ClassLoader getPatchesClassloader() {
        if (configProperties.getPatches() == null || configProperties.getPatches().isEmpty()) {
            return null;
        }
        if (LOGGER.isDebugEnabled()) {
            configProperties.getPatches()
                .forEach(p -> LOGGER.debug("[getPatchesClassloader] -> patch with high precedence {} ", p));
        }
        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        URL[] patches = Stream.of(springClassLoader.getURLs()).filter(u -> {
            String depName = getName(u.getFile().replaceAll(WITHIN_JAR_REGEX, "").replaceAll("\\.jar", ""));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[getPatchesClassloader] -> dependency name to filter found on classloader {} ", depName);
            }
            return configProperties.getPatches().stream().anyMatch(p -> p.trim().equals(depName));
        }).toArray(URL[]::new);
        if (patches.length == 0) {
            return null;
        }
        for (URL u : patches) {
            LOGGER.info("Loaded patch dependency {} into high priority classloader ", u.getPath());
        }
        return new URLClassLoader(patches);
    }

    void deployArtifact(DeploymentTask deploymentTask,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        try {
            muleContainer.getDeploymentService().getLock().lock();
            // apply deployment properties
            Properties deploymentProperties = new Properties();
            deploymentProperties.put(MULE_LAZY_INIT_DEPLOYMENT_PROPERTY, valueOf(
                Optional.ofNullable(lazyInitializationEnabled).orElse(configProperties.isLazyInitializationEnabled())));
            deploymentProperties.put(MULE_LAZY_INIT_ENABLE_XML_VALIDATIONS_DEPLOYMENT_PROPERTY,
                valueOf(Optional.ofNullable(xmlValidationsEnabled).orElse(configProperties.isXmlValidationsEnabled())));
            deploymentProperties.put(MULE_LAZY_CONNECTIONS_DEPLOYMENT_PROPERTY, valueOf(
                Optional.ofNullable(lazyConnectionsEnabled).orElse(configProperties.isLazyConnectionsEnabled())));
            // deploy artifact
            deploymentTask.deploy(deploymentProperties);
        } catch (Exception e) {
            throw new DeployArtifactException("Unable to deploy actifact!", e);
        } finally {
            if (muleContainer.getDeploymentService().getLock().isHeldByCurrentThread()) {
                muleContainer.getDeploymentService().getLock().unlock();
            }
        }
    }

    void executeWithinClassLoader(ClassLoader cl, ContainerTask runnable) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            runnable.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    void cleanUpFolder(File folder) {
        try {
            deleteDirectory(folder);
        } catch (Exception e) {
            LOGGER.warn("Unable to full cleanUpFolder. Error was: " + e.getMessage());
        }
    }

    private void deployMuleArtifacts() {
        try {
            if (configProperties.getDomains() != null) {
                for (Resource res : configProperties.getDomains()) {
                    File f;
                    if (res.isFile()) {
                        f = res.getFile();
                    } else {
                        f = StorageUtils.storeArtifactTemp(res.getFilename(), res.getInputStream());
                    }
                    if (!isDeployed(getDomainsFolder(), f)) {
                        deployDomain(f, null, null, null);
                    }
                }
            }
            if (configProperties.getApps() != null) {
                for (Resource res : configProperties.getApps()) {
                    File f;
                    if (res.isFile()) {
                        f = res.getFile();
                    } else {
                        f = StorageUtils.storeArtifactTemp(res.getFilename(), res.getInputStream());
                    }
                    if (!isDeployed(getAppsFolder(), f)) {
                        deployApplication(f, null, null, null);
                    }
                }
            }
        } catch (IOException e) {
            throw new DeployArtifactException("Unable to deploy mule artifacts!", e);
        }
    }

    private boolean isDeployed(File artifactDeploymentFolder, File artifactFile) {
        return new File(artifactDeploymentFolder, artifactFile.getName().replace(".jar", "") + ARTIFACT_ANCHOR_SUFFIX)
                   .exists();
    }

    private static File getLogFolder() {
        return new File(getMuleBaseFolder(), LOGS_FORDER);
    }

}
