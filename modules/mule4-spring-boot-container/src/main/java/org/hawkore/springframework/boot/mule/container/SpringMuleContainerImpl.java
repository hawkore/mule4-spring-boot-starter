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
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hawkore.springframework.boot.mule.config.MuleConfigProperties;
import org.hawkore.springframework.boot.mule.exception.DeployArtifactException;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader;
import org.hawkore.springframework.boot.mule.utils.CompositeClassLoader.DefaultStrategy;
import org.hawkore.springframework.boot.mule.utils.StorageUtils;
import org.mule.runtime.api.util.MuleSystemProperties;
import org.mule.runtime.core.api.config.MuleManifest;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.module.artifact.api.classloader.net.MuleUrlStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.SpringVersion;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import static java.lang.String.valueOf;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;

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
 * Embedded Spring Mule Container base implementation
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public abstract class SpringMuleContainerImpl implements SpringMuleContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringMuleContainerImpl.class);
    /**
     * The constant ARTIFACT_ANCHOR_SUFFIX.
     */
    public static final String ARTIFACT_ANCHOR_SUFFIX = "-anchor.txt";
    /**
     * The constant LOGS_FORDER.
     */
    private static final String LOGS_FORDER = "logs";
    /**
     * Expression to remove from resources' names within spring boot executable jar
     */
    private static final String WITHIN_JAR = "!/";
    private static final String JAR_EXTENSION = ".jar";
    private static final String MULE_SERVICE_SUFFIX = "-mule-service";
    private static final String MULE_DOMAIN_SUFFIX = "-mule-domain";
    private static final String MULE_APPLICATION_SUFFIX = "-mule-application";
    private static final String JAR_HANDLER = "org.springframework.boot.loader.jar.Handler";
    private static final String FILE_HANDLER = "sun.net.www.protocol.file.Handler";
    private static final String JAR_PROTOCOL = "jar";
    private static final String FILE_PROTOCOL = "file";
    private static final String ALERT_MESSAGE_ARTIFACT_WITHIN_CLASSPATH =
        "{} within classpath seems to be a MULE {}. Please, remove it as direct dependency and add it as"
            + " resource. After that, if your want to deploy it, consider to enable auto-deploy mule artifacts "
            + "('mule.autoDeployArtifacts=true') or to add this file name to 'mule.apps' property";
    private static final String ALERT_MESSAGE_PATCH_WITHIN_CLASSPATH =
        "{} seems to be a MULE PATCH. Consider to enable auto-load patches ('mule.autoLoadPatches=true') or to add "
            + "this patch name to 'mule.patches' property in order to load it into high priority classloader";
    /**
     * The Config properties.
     */
    @Autowired
    private MuleConfigProperties configProperties;
    // only one running Mule Runtime instance is allowed within same JVM
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private ClassLoader containerClassLoader;

    /**
     * On application event.
     *
     * @param event
     *     the event
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Starting Mule Runtime by ContextRefreshedEvent");
            }
            start();
        } else if (event instanceof ContextClosedEvent) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Stopping Mule Runtime by ContextClosedEvent");
            }
            stop();
        }
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
        checkRunning();
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
        checkRunning();
        return getDomainFolder(domain).exists();
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
        checkRunning();
        deployApplication(appFile.toURI(), lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
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
        checkRunning();
        deployDomain(domainFile.toURI(), lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
    }

    /**
     * Whether Mule is ready.
     *
     * @return the boolean
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Interface for running deployment tasks within the container class loader.
     */
    @FunctionalInterface
    public interface DeploymentTask {

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
    public interface ContainerTask {

        /**
         * Run.
         *
         * @throws Exception
         *     the exception
         */
        void run() throws Exception; //NOSONAR

    }

    /**
     * Start.
     */
    @Override
    public synchronized void start() {
        try {
            if (started.getAndSet(true)) {
                LOGGER.warn("Mule Runtime already started!");
                return;
            }
            LOGGER.info("Starting Mule Runtime [{} {} build {} + Spring Boot {} + Spring Framework {}]...",
                MuleManifest.getProductName(), MuleManifest.getProductVersion(), MuleManifest.getBuildNumber(),
                SpringBootVersion.getVersion(), SpringVersion.getVersion());
            setUpEnvironmentAndStart();
            running.set(true);
            LOGGER.info("Mule Runtime is ready");
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Unable to start Mule Runtime", e);
        }
        // ORDER MATTERS!!
        // 1. deploy domains found on mule.domains property
        deployMuleDomains();
        // 2. deploy applications found on mule.apps property
        deployMuleApplications();
    }

    /**
     * Stop.
     */
    @Override
    public synchronized void stop() {
        if (!running.getAndSet(false) && !started.get()) {
            LOGGER.warn("Mule Runtime already stopped!");
            return;
        }
        try {
            LOGGER.info("Stopping Mule Runtime ...");
            disposeMuleContainer(containerClassLoader);
        } finally {
            started.set(false);
        }
    }

    /**
     * Container class loader.
     *
     * @return the class loader
     */
    public ClassLoader containerClassLoader() {
        return containerClassLoader;
    }

    /**
     * Check running.
     */
    protected void checkRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Unable to process request, Mule Runtime is not running!");
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
                throw new IllegalArgumentException("mule.base must be provided!");
            }

            setProperty(MULE_HOME_DIRECTORY_PROPERTY, configProperties.getBase().toURI().getPath());

            if (configProperties.isSimpleLog()) {
                setProperty(MuleSystemProperties.MULE_SIMPLE_LOG, "true");
            } else {
                clearProperty(MuleSystemProperties.MULE_SIMPLE_LOG);
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
            // Create a composite classloader to avoid loading mule services or patches from container classloader.
            containerClassLoader = new CompositeClassLoader(buildContainerClassloader());
            // Create a high priority patches classloader to ensure those patches take precedence over rest of
            // classes/resources
            ClassLoader patchesClassLoader = buildPatchesClassloader();
            if (patchesClassLoader != null) {
                containerClassLoader = new CompositeClassLoader(patchesClassLoader, containerClassLoader);
            }
            initMuleContainer(containerClassLoader);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start Mule Runtime container!", e);
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
                LOGGER.trace("[installOrUpgradeServices] -> dependency to filter found on classloader {} ",
                    u.getPath());
            }
            return u.getFile().replace(WITHIN_JAR, "").endsWith("-mule-service.jar");
        }).collect(Collectors.toList());

        // extract Mule services as they must be loaded from local file system (Mule Runtime requirement).
        // We will do it always to allow update Mule runtime version on an existing mule forder.
        for (URL url : services) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Installing Mule service {} ...", url.getPath());
            }
            File destinationFile = new File(getServicesFolder(),
                getName(url.getFile().replace("-mule-service.jar", "").replace(WITHIN_JAR, "")));
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
                String fileName = res.getFilename();
                if (fileName != null) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Installing Mule server plugin {} ...", res.getURI());
                    }
                    File destinationFile = new File(getServerPluginsFolder(), getName(fileName.replace(".zip", "")));
                    destinationFile.mkdirs();
                    StorageUtils.unzip(res.getInputStream(), destinationFile);
                }
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
        String[] patchesPrefix = Optional.ofNullable(configProperties.getPatchesPrefix())
                                     .orElse(Collections.emptyList()).stream().toArray(String[]::new);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[patchesPrefix] -> patches prefix {} ", Arrays.toString(patchesPrefix));
        }
        URL[] patches = Stream.of(springClassLoader.getURLs()).filter(u -> {
            String depName = getName(u.getFile().replace(WITHIN_JAR, "").replace(JAR_EXTENSION, ""));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[getPatchesClassloader] -> dependency name to filter found on classloader {} ", depName);
            }
            if (configProperties.isAutoLoadPatches() && StringUtils.startsWithAny(depName, patchesPrefix)) {
                LOGGER.info(
                    "{} dependency seems to be a MULE PATCH. Will be auto-loaded into high priority class loader",
                    depName);
                patchNames.remove(depName);
                return true;
            }
            return patchNames.remove(depName);
        }).sorted((a, b) -> {
            if (!emptyPatches) {
                String aName = getName(a.getFile().replace(WITHIN_JAR, "").replace(JAR_EXTENSION, ""));
                String bName = getName(b.getFile().replace(WITHIN_JAR, "").replace(JAR_EXTENSION, ""));
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
        // original class loader
        URLClassLoader springClassLoader = (URLClassLoader)this.getClass().getClassLoader();

        String[] patchesPrefix = Optional.ofNullable(configProperties.getPatchesPrefix())
                                     .orElse(Collections.emptyList()).stream().toArray(String[]::new);

        URL[] libs = Stream.of(springClassLoader.getURLs()).filter(u -> {
            String depName = getName(u.getFile().replace(WITHIN_JAR, "").replace(JAR_EXTENSION, ""));
            if (
                // remove provided MULE patches/libs from classloader
                (!CollectionUtils.isEmpty(configProperties.getPatches()) && configProperties.getPatches().stream()
                                                                                .anyMatch(
                                                                                    p -> p.trim().equals(depName)))
                    // Services must be loaded from local file system by Mule Runtime (Mule Runtime requirement)
                    || depName.endsWith(MULE_SERVICE_SUFFIX)) {
                return false;
            }
            // remove Mule Domains from classpath whether present, must be loaded as resource
            if (depName.endsWith(MULE_DOMAIN_SUFFIX)) {
                LOGGER.error(ALERT_MESSAGE_ARTIFACT_WITHIN_CLASSPATH, getName(u.getFile().replace(WITHIN_JAR, "")),
                    "domain");
                return false;
            }
            // remove Mule Applications from classpath whether present, must be loaded as resource
            if (depName.endsWith(MULE_APPLICATION_SUFFIX)) {
                LOGGER.error(ALERT_MESSAGE_ARTIFACT_WITHIN_CLASSPATH, getName(u.getFile().replace(WITHIN_JAR, "")),
                    "application");
                return false;
            }
            // remove auto-loaded MULE patches from container classloader
            if (StringUtils.startsWithAny(depName, patchesPrefix)) {
                if (configProperties.isAutoLoadPatches()) {
                    // will be auto-loaded into high priority class loader
                    return false;
                }
                LOGGER.warn(ALERT_MESSAGE_PATCH_WITHIN_CLASSPATH, depName);
            }
            return true;
        }).toArray(URL[]::new);

        // filtered class loader
        CompositeClassLoader allowedLibsClassLoader = new CompositeClassLoader(new URLClassLoader(libs));

        return new CompositeClassLoader(springClassLoader, new DefaultStrategy(
            // class exists on filtered class loader
            s -> {
                try {
                    allowedLibsClassLoader.loadClass(s);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }, s -> false,
            // resource exists on filtered class loader
            s -> allowedLibsClassLoader.getResource(s) != null, s -> false));
    }

    /**
     * Deploy artifact.
     *
     * @param deploymentTask
     *     the deployment task
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     */
    public synchronized void deployArtifact(DeploymentTask deploymentTask,
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
            throw new DeployArtifactException("Unable to deploy actifact", e);
        }
    }

    /**
     * Execute within class loader.
     *
     * @param cl
     *     the cl
     * @param runnable
     *     the runnable
     */
    public void executeWithinClassLoader(ClassLoader cl, ContainerTask runnable) {
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
            // Find mule applications within resources classpath
            final Set<Resource> artifacts = Optional.ofNullable(configProperties.getApps()).orElse(new HashSet<>());

            PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(
                this.getClass().getClassLoader());

            Stream.of(resourcePatternResolver.getResources("classpath*:**/*" + MULE_APPLICATION_SUFFIX + JAR_EXTENSION))
                .forEach(r -> {
                    if (artifacts.stream().noneMatch(a -> getName(a.getFilename()).equals(getName(r.getFilename())))) {
                        if (configProperties.isAutoDeployArtifacts()) {
                            LOGGER.info("{} within resources classpath seems to be a MULE application and it will be "
                                            + "auto-deployed", getName(r.getFilename()));
                            // add to apps to be auto-deployed
                            artifacts.add(r);
                        } else {
                            LOGGER.warn(
                                "{} within resources classpath seems to be a MULE application but it will not be "
                                    + "auto-deployed. Consider to enable auto-deploy mule artifacts ('mule"
                                    + ".autoDeployArtifacts=true') or to add this file name to 'mule"
                                    + ".domains' property in order to deploy it!", getName(r.getFilename()));
                        }
                    }
                });

            configProperties.setApps(artifacts);

            for (Resource res : configProperties.getApps()) {
                File f = StorageUtils.storeArtifactTempOrGet(res);
                if (!isDeployed(getAppsFolder(), f)) {
                    deployApplication(f, null, null, null);
                } else {
                    LOGGER.warn(
                        "Provided Mule application '{}' already deployed. Set mule.cleanStartup=true to re-deploy"
                            + " it when Mule starts.", getName(f.getPath()));
                }
            }
        } catch (Exception e) {
            throw new DeployArtifactException("Unable to deploy mule applications at startup!", e);
        }
    }

    private void deployMuleDomains() {
        try {
            // Find mule domains within resources classpath
            final Set<Resource> artifacts = Optional.ofNullable(configProperties.getDomains()).orElse(new HashSet<>());

            PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(
                this.getClass().getClassLoader());

            Stream.of(resourcePatternResolver.getResources("classpath*:**/*" + MULE_DOMAIN_SUFFIX + JAR_EXTENSION))
                .forEach(r -> {
                    if (artifacts.stream().noneMatch(a -> getName(a.getFilename()).equals(getName(r.getFilename())))) {
                        if (configProperties.isAutoDeployArtifacts()) {
                            LOGGER.info(
                                "{} within resources classpath seems to be a MULE domain and it will be auto-deployed",
                                getName(r.getFilename()));
                            // add to domains to be auto-deployed
                            artifacts.add(r);
                        } else {
                            LOGGER.warn("{} within resources classpath seems to be a MULE domain but it will not be "
                                            + "auto-deployed. Consider to enable auto-deploy mule artifacts ('mule"
                                            + ".autoDeployArtifacts=true') or to add this file name to 'mule"
                                            + ".domains' property in order to deploy it!", getName(r.getFilename()));
                        }
                    }
                });

            configProperties.setDomains(artifacts);

            for (Resource res : configProperties.getDomains()) {
                File f = StorageUtils.storeArtifactTempOrGet(res);
                if (!isDeployed(getDomainsFolder(), f)) {
                    deployDomain(f, null, null, null);
                } else {
                    LOGGER.warn("Provided Mule domain '{}' already deployed. Set mule.cleanStartup=true to re-deploy"
                                    + " it when Mule starts.", getName(f.getPath()));
                }
            }
        } catch (Exception e) {
            throw new DeployArtifactException("Unable to deploy mule domains at startup!", e);
        }
    }

    private boolean isDeployed(File artifactDeploymentFolder, File artifactFile) {
        return new File(artifactDeploymentFolder,
            artifactFile.getName().replace(".jar", "") + ARTIFACT_ANCHOR_SUFFIX).exists();
    }

    private static File getLogFolder() {
        return new File(getMuleBaseFolder(), LOGS_FORDER);
    }

    /**
     * Register spring boot jar handler.
     *
     * @param classLoader
     *     the class loader
     * @throws InvocationTargetException
     *     the invocation target exception
     * @throws NoSuchMethodException
     *     the no such method exception
     * @throws InstantiationException
     *     the instantiation exception
     * @throws IllegalAccessException
     *     the illegal access exception
     */
    public void registerSpringBootJarHandler(ClassLoader classLoader)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[classloader] -> {} ", this.getClass().getClassLoader().getClass().getName());
            }
            // register handlers to access classes/resources within executable spring boot jar
            //@formatter:off
            MuleUrlStreamHandlerFactory.registerHandler(JAR_PROTOCOL, (URLStreamHandler)ClassUtils.instantiateClass(JAR_HANDLER, null, classLoader));
            MuleUrlStreamHandlerFactory.registerHandler(FILE_PROTOCOL, (URLStreamHandler)ClassUtils.instantiateClass(FILE_HANDLER, null, classLoader));
            //@formatter:on
        } catch (ClassNotFoundException e) {
            // ignored, not within executable spring boot jar
        }
    }

}
