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
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandler;
import java.util.ArrayList;
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
import org.springframework.util.CollectionUtils;

import static java.lang.String.valueOf;
import static java.lang.System.setProperty;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FilenameUtils.getName;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppFolder;
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
     * Regular expression to remove from resources' names within spring boot executable jar
     */
    private static final String WITHIN_JAR_REGEX = "\\!\\/";
    /**
     * Regular expression to remove jar extension
     */
    private static final String JAR_EXTENSION = "\\.jar";
    /**
     * The Config properties.
     */
    @Autowired
    private MuleConfigProperties configProperties;
    // only one running Mule Runtime instance is allowed within same JVM
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
            start();
        } else if (event instanceof ContextClosedEvent) {
            stop();
        }
    }

    /**
     * Whether application is deployed and running.
     *
     * @param application
     *     the application
     * @return the boolean
     */
    @Override
    public boolean isApplicationDeployed(String application) {
        checkStopped();
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
        checkStopped();
        return Optional.ofNullable(muleContainer.getDeploymentService().findDomain(domain))
                   .map(d -> new File(getDomainsFolder(), domain + ARTIFACT_ANCHOR_SUFFIX).exists()).orElse(false);
    }

    /**
     * Whether application is installed (application directory exists).
     *
     * @param application
     *     the application
     * @return the boolean
     */
    @Override
    public boolean isApplicationInstalled(String application) {
        checkStopped();
        return getAppFolder(application).exists();
    }

    /**
     * Whether domain is installed (domain directory exists).
     *
     * @param domain
     *     the domain
     * @return the boolean
     */
    @Override
    public boolean isDomainInstalled(String domain) {
        checkStopped();
        return getDomainFolder(domain).exists();
    }

    /**
     * Gets installed applications.
     *
     * @return the applications
     */
    @Override
    public List<Application> getApplications() {
        checkStopped();
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
        checkStopped();
        return muleContainer.getDeploymentService().getDomains().stream()
                   .filter(f -> isDomainInstalled(f.getArtifactName())).map(
                f -> new Domain().setName(f.getArtifactName()).setStatus(isDomainDeployed(f.getArtifactName())
                                                                             ? ApplicationStatus.STARTED
                                                                             : ApplicationStatus.DEPLOYMENT_FAILED)
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
     * @throws IOException
     *     the io exception
     */
    @Override
    public synchronized void undeployApplication(String applicationName) throws IOException {
        checkStopped();
        muleContainer.getDeploymentService().undeploy(applicationName);
        // ensure full removal from disk
        deleteDirectory(getAppFolder(applicationName));
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
    public synchronized void undeployDomain(String domainName) throws IOException {
        checkStopped();
        muleContainer.getDeploymentService().undeployDomain(domainName);
        // ensure full removal from disk
        deleteDirectory(getDomainFolder(domainName));
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

    protected synchronized void start() {
        try {
            if (started.getAndSet(true)) {
                LOGGER.warn("Mule Runtime already started");
                return;
            }
            LOGGER.info("Starting Mule Runtime ...");
            setUpEnvironmentAndStart();
        } catch (Exception e) {
            started.set(false);
            throw new IllegalStateException("Unable to start Mule Runtime", e);
        }
        // ORDER MATTERS!!
        // First: deploy domains found on mule.domains property
        deployMuleDomains();
        // Second: deploy applications found on mule.apps property
        deployMuleApplications();
    }

    protected synchronized void stop() {
        if (muleContainer != null && started.getAndSet(false)) {
            LOGGER.info("Stopping Mule Runtime ...");
            executeWithinClassLoader(containerClassLoader, () -> {
                muleContainer.stop();
                muleContainer.getContainerClassLoader().dispose();
            });
        }
    }

    protected ClassLoader containerClassLoader() {
        return containerClassLoader;
    }

    private void checkStopped() {
        if (!started.get()) {
            throw new IllegalStateException("Mule Runtime container is not running!!");
        }
    }

    private void setUpEnvironmentAndStart() {
        try {
            // register SpringBootJarHandler for packaged spring boot Mule Runtime
            registerSpringBootJarHandler(null);

            // this is used to signal that we are running in embedded mode.
            // Class loader model loader will not use try to use the container repository.
            setProperty("mule.mode.embedded", "true");

            // Disable log4j2 JMX MBeans since it will fail when trying to recreate the container
            setProperty("log4j2.disable.jmx", "true");

            if (configProperties.getBase() == null) {
                throw new IllegalArgumentException("mule.base must be provided!!");
            }

            setProperty(MULE_HOME_DIRECTORY_PROPERTY, configProperties.getBase().toURI().getPath());

            if (configProperties.isSimpleLog()) {
                setProperty(MuleSystemProperties.MULE_SIMPLE_LOG, "true");
            }
            // ensure required folders exist
            getMuleBaseFolder().mkdirs();
            getConfFolder().mkdirs();
            getLogFolder().mkdirs();

            if (configProperties.isCleanStartup()) {
                LOGGER.info("Cleaning-up artifact forders before start Mule Runtime ...");
                StorageUtils.cleanUpFolder(getAppsFolder());
                StorageUtils.cleanUpFolder(getDomainsFolder());
            }
            getDomainsFolder().mkdirs();
            getDomainFolder("default").mkdirs();
            getAppsFolder().mkdirs();
            // extract Mule services as they must be loaded from local file system (Mule Runtime requirement).
            // We will do it always to allow update Mule runtime version on an existing mule forder.
            installOrUpgradeServices();
            // extract Mule server plugins as they must be loaded from local file system (Mule Runtime requirement).
            // We will do it always to allow update Mule runtime version on an existing mule forder.
            installOrUpgradeServerPlugins();
            muleContainer = new MuleContainer(new String[0]);
            // Create a composite classloader to avoid loading mule services or patches from container classloader.
            containerClassLoader = new CompositeClassLoader(buildContainerClassloader());
            // Create a high priority patches classloader to ensure those patches take precedence over rest of
            // classes/resources
            ClassLoader patchesClassLoader = buildPatchesClassloader();
            if (patchesClassLoader != null) {
                containerClassLoader = new CompositeClassLoader(patchesClassLoader, containerClassLoader);
            }
            // Start Mule Runtime container, do not register shutdown hook since it will try to kill the JVM
            executeWithinClassLoader(containerClassLoader, () -> muleContainer.start(false));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start Mule Runtime container!!", e);
        }
    }

    // load mule services URLs from classloader and install them on local file system
    private void installOrUpgradeServices() throws IOException {
        // delete services folder to allow update mule runtime on existing base folder
        StorageUtils.cleanUpFolder(getServicesFolder());
        getServicesFolder().mkdirs();

        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        List<URL> services = Stream.of(springClassLoader.getURLs()).filter(u -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER
                    .trace("[installOrUpgradeServices] -> dependency to filter found on classloader {} ", u.getPath());
            }
            return u.getFile().replaceAll(WITHIN_JAR_REGEX, "").endsWith("-mule-service.jar");
        }).collect(Collectors.toList());

        // extract Mule services as they must be loaded from local file system (Mule Runtime requirement).
        // We will do it always to allow update Mule runtime version on an existing mule forder.
        for (URL url : services) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Installing Mule service {} ...", url.getPath());
            }
            File destinationFile = new File(getServicesFolder(),
                getName(url.getFile().replaceAll("-mule-service\\.jar", "").replaceAll(WITHIN_JAR_REGEX, "")));
            destinationFile.mkdirs();
            StorageUtils.unzip(url.openStream(), destinationFile);
        }
    }

    // load mule server plugins Resources from configuration and install them on local file system
    private void installOrUpgradeServerPlugins() throws IOException {
        // delete server plugins folder to allow update mule runtime on existing base folder
        StorageUtils.cleanUpFolder(getServerPluginsFolder());
        getServerPluginsFolder().mkdirs();

        // extract Mule server plugins as they must be loaded from local file system (Mule Runtime requirement).
        // We will do it always to allow update Mule runtime version on an existing mule forder.
        if (configProperties.getServerPlugins() != null) {
            for (Resource res : configProperties.getServerPlugins()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Installing Mule server plugin {} ...", res.getURI());
                }
                File destinationFile = new File(getServerPluginsFolder(),
                    getName(res.getFilename().replace(".zip", "")));
                destinationFile.mkdirs();
                StorageUtils.unzip(res.getInputStream(), destinationFile);
            }
        }
    }

    // load mule patches URLs from classloader
    private ClassLoader buildPatchesClassloader() {
        List<String> patchNames = new ArrayList<>();
        boolean emptyPatches = CollectionUtils.isEmpty(configProperties.getPatches());
        if (!emptyPatches) {
            if (LOGGER.isDebugEnabled()) {
                configProperties.getPatches().forEach(
                    p -> LOGGER.debug("[getPatchesClassloader] -> provided patch name with high priority {} ", p));
            }
            patchNames.addAll(configProperties.getPatches());
        }
        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        URL[] patches = Stream.of(springClassLoader.getURLs()).filter(u -> {
            String depName = getName(u.getFile().replaceAll(WITHIN_JAR_REGEX, "").replaceAll(JAR_EXTENSION, ""));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[getPatchesClassloader] -> dependency name to filter found on classloader {} ", depName);
            }
            if (configProperties.isAutoLoadPatches() && (depName.startsWith("MULE-") || depName.startsWith("SE-"))) {
                LOGGER.info(
                    "{} dependency seems to be a MULE PATCH. Will be auto-loaded into high priority class loader!",
                    depName);
                return true;
            }
            return patchNames.remove(depName);
        }).sorted((a, b) -> {
            if (!emptyPatches) {
                String aName = getName(a.getFile().replaceAll(WITHIN_JAR_REGEX, "").replaceAll(JAR_EXTENSION, ""));
                String bName = getName(b.getFile().replaceAll(WITHIN_JAR_REGEX, "").replaceAll(JAR_EXTENSION, ""));
                // sorting based on provided ordered list of patches
                int aIndex = configProperties.getPatches().indexOf(aName);
                int bIndex = configProperties.getPatches().indexOf(bName);
                return Integer.compare(aIndex, bIndex);
            }
            // default no sort
            return 0;
        }).toArray(URL[]::new);
        // warn not found provided patches
        for (String u : patchNames) {
            LOGGER.warn("Provided patch name {} was not found on classloader. Consider to remove it from provided 'mule"
                            + ".paches' property", u);
        }
        if (patches.length == 0) {
            return null;
        }
        for (URL u : patches) {
            LOGGER.info("Loaded patch dependency {} into high priority classloader ", u.getPath());
        }
        return new URLClassLoader(patches);
    }

    private ClassLoader buildContainerClassloader() {
        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();
        URL[] libs = Stream.of(springClassLoader.getURLs()).filter(u -> {
            String depName = getName(u.getFile().replaceAll(WITHIN_JAR_REGEX, "").replaceAll(JAR_EXTENSION, ""));
            // remove provided MULE patches/libs from classloader
            if (!CollectionUtils.isEmpty(configProperties.getPatches()) && configProperties.getPatches().stream()
                                                                               .anyMatch(
                                                                                   p -> p.trim().equals(depName))) {
                return false;
            }
            // Services must be loaded from local file system by Mule Runtime (Mule Runtime requirement)
            if (depName.endsWith("-mule-service")) {
                return false;
            }
            // remove auto-loaded MULE patches from container classloader
            if (depName.startsWith("MULE-") || depName.startsWith("SE-")) {
                if (configProperties.isAutoLoadPatches()) {
                    // will be auto-loaded into high priority class loader
                    return false;
                } else {
                    LOGGER.warn("{} seems to be a MULE PATCH. Consider to enable auto-load patches ('mule"
                                    + ".autoLoadPatches=true') or to add this patch name to 'mule"
                                    + ".patches' property in order to load it into high priority classloader!",
                        depName);
                }
            }
            return true;
        }).toArray(URL[]::new);
        return new URLClassLoader(libs);
    }

    protected synchronized void deployArtifact(DeploymentTask deploymentTask,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled) {
        try {
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
        }
    }

    protected void executeWithinClassLoader(ClassLoader cl, ContainerTask runnable) {
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

    private void deployMuleApplications() {
        try {
            if (configProperties.getApps() != null) {
                for (Resource res : configProperties.getApps()) {
                    File f = res.isFile()
                                 ? res.getFile()
                                 : StorageUtils.storeArtifactTemp(res.getFilename(), res.getInputStream());
                    if (!isDeployed(getAppsFolder(), f)) {
                        deployApplication(f, null, null, null);
                    } else {
                        LOGGER.warn(
                            "Provided Mule application '{}' already deployed. Set mule.cleanStartup=true to re-deploy"
                                + " it when Mule starts.", getName(f.getPath()));
                    }
                }
            }
        } catch (IOException e) {
            throw new DeployArtifactException("Unable to deploy mule applications at startup!", e);
        }
    }

    private void deployMuleDomains() {
        try {
            if (configProperties.getDomains() != null) {
                for (Resource res : configProperties.getDomains()) {
                    File f = res.isFile()
                                 ? res.getFile()
                                 : StorageUtils.storeArtifactTemp(res.getFilename(), res.getInputStream());
                    if (!isDeployed(getDomainsFolder(), f)) {
                        deployDomain(f, null, null, null);
                    } else {
                        LOGGER.warn(
                            "Provided Mule domain '{}' already deployed. Set mule.cleanStartup=true to re-deploy"
                                + " it when Mule starts.", getName(f.getPath()));
                    }
                }
            }
        } catch (IOException e) {
            throw new DeployArtifactException("Unable to deploy mule domains at startup!", e);
        }
    }

    private boolean isDeployed(File artifactDeploymentFolder, File artifactFile) {
        return new File(artifactDeploymentFolder, artifactFile.getName().replace(".jar", "") + ARTIFACT_ANCHOR_SUFFIX)
                   .exists();
    }

    private static File getLogFolder() {
        return new File(getMuleBaseFolder(), LOGS_FORDER);
    }

    protected void registerSpringBootJarHandler(ClassLoader classLoader)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[classloader] -> {} ", this.getClass().getClassLoader().getClass().getName());
            }
            // register handlers to access classes/resources within executable spring boot jar
            //@formatter:off
            MuleUrlStreamHandlerFactory.registerHandler("jar", (URLStreamHandler)ClassUtils.instantiateClass("org.springframework.boot.loader.jar.Handler", null , classLoader));
            MuleUrlStreamHandlerFactory.registerHandler("file", (URLStreamHandler)ClassUtils.instantiateClass("sun.net.www.protocol.file.Handler", null , classLoader));
            //@formatter:on
        } catch (ClassNotFoundException e) {
            // ignored, not within executable spring boot jar
        }
    }

}
