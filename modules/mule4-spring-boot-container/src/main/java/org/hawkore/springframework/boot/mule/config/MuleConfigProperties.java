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
package org.hawkore.springframework.boot.mule.config;

import java.io.File;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Mule configuration properties
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@Component
@ConfigurationProperties(prefix = "mule", ignoreUnknownFields = true)
public class MuleConfigProperties {

    /** Mule base directory */
    private File base;
    /** Mule domains to be deployed into Mule runtime container at start time */
    private List<Resource> domains;
    /** Mule applications to be deployed into Mule runtime container at start time */
    private List<Resource> apps;
    /** Mule server plugins to be deployed before Mule runtime starts */
    private List<Resource> serverPlugins;
    /** Mule artifacts initialization flags */
    private boolean lazyInitializationEnabled = false;
    private boolean xmlValidationsEnabled = true;
    private boolean lazyConnectionsEnabled = true;
    /** Enable flag simple log (global) for mule runtime logging. By default true **/
    private boolean simpleLog = true;
    /**
     * Remove deployments before start Mule Runtime. By default false.
     * Useful to start a clean Mule runtime.
     */
    private boolean cleanStartup = false;
    /**
     * List of Mule Runtime patches that need to take precedence in the Mule container classloader
     */
    private List<String> patches;
    /**
     * Auto load MULE PATCHES (dependencies starting with MULE- or SE-) into high priority classloader
     */
    private boolean autoLoadPatches = true;

    /**
     * Gets base.
     *
     * @return the base
     */
    public File getBase() {
        return base;
    }

    /**
     * Sets base.
     *
     * @param base
     *     the base
     * @return this for chaining
     */
    public MuleConfigProperties setBase(File base) {
        this.base = base;
        return this;
    }

    /**
     * Gets domains.
     *
     * @return the domains
     */
    public List<Resource> getDomains() {
        return domains;
    }

    /**
     * Sets domains.
     *
     * @param domains
     *     the domains
     * @return this for chaining
     */
    public MuleConfigProperties setDomains(List<Resource> domains) {
        this.domains = domains;
        return this;
    }

    /**
     * Gets apps.
     *
     * @return the apps
     */
    public List<Resource> getApps() {
        return apps;
    }

    /**
     * Sets apps.
     *
     * @param apps
     *     the apps
     * @return this for chaining
     */
    public MuleConfigProperties setApps(List<Resource> apps) {
        this.apps = apps;
        return this;
    }

    /**
     * Is lazy initialization enabled boolean.
     *
     * @return the boolean
     */
    public boolean isLazyInitializationEnabled() {
        return lazyInitializationEnabled;
    }

    /**
     * Sets lazy initialization enabled.
     *
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @return this for chaining
     */
    public MuleConfigProperties setLazyInitializationEnabled(boolean lazyInitializationEnabled) {
        this.lazyInitializationEnabled = lazyInitializationEnabled;
        return this;
    }

    /**
     * Is xml validations enabled boolean.
     *
     * @return the boolean
     */
    public boolean isXmlValidationsEnabled() {
        return xmlValidationsEnabled;
    }

    /**
     * Sets xml validations enabled.
     *
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @return this for chaining
     */
    public MuleConfigProperties setXmlValidationsEnabled(boolean xmlValidationsEnabled) {
        this.xmlValidationsEnabled = xmlValidationsEnabled;
        return this;
    }

    /**
     * Is lazy connections enabled boolean.
     *
     * @return the boolean
     */
    public boolean isLazyConnectionsEnabled() {
        return lazyConnectionsEnabled;
    }

    /**
     * Sets lazy connections enabled.
     *
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     * @return this for chaining
     */
    public MuleConfigProperties setLazyConnectionsEnabled(boolean lazyConnectionsEnabled) {
        this.lazyConnectionsEnabled = lazyConnectionsEnabled;
        return this;
    }

    /**
     * Is simple log boolean.
     *
     * @return the boolean
     */
    public boolean isSimpleLog() {
        return simpleLog;
    }

    /**
     * Sets simple log.
     *
     * @param simpleLog
     *     the simple log
     * @return this for chaining
     */
    public MuleConfigProperties setSimpleLog(boolean simpleLog) {
        this.simpleLog = simpleLog;
        return this;
    }

    /**
     * Is clean startup boolean.
     *
     * @return the boolean
     */
    public boolean isCleanStartup() {
        return cleanStartup;
    }

    /**
     * Sets clean startup.
     *
     * @param cleanStartup
     *     the clean startup
     * @return this for chaining
     */
    public MuleConfigProperties setCleanStartup(boolean cleanStartup) {
        this.cleanStartup = cleanStartup;
        return this;
    }

    /**
     * Gets patches.
     *
     * @return the patches
     */
    public List<String> getPatches() {
        return patches;
    }

    /**
     * List of Mule Runtime patches that need to take precedence in the Mule container class loader.
     * <p>
     * Example:
     * <p>
     * If you want to patch Mule Runtime by adding these dependencies:
     * <pre>
     * &lt;dependency&gt;
     *  &lt;groupId&gt;org.mule.patches&lt;/groupId&gt;
     *  &lt;artifactId&gt;APATCH-086&lt;/artifactId&gt;
     *  &lt;version&gt;2.0.0&lt;/version&gt;
     * &lt;/dependency&gt;
     * &lt;dependency&gt;
     *  &lt;groupId&gt;org.mule.patches&lt;/groupId&gt;
     *  &lt;artifactId&gt;APATCH-007&lt;/artifactId&gt;
     *  &lt;version&gt;1.0.0&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     * <p>
     * Then you must provide <b>mule.patches</b> property as follows (artifactId-version):
     * <pre>
     * mule.patches[0]=APATCH-086-2.0.0
     * mule.patches[1]=APATCH-007-1.0.0
     * </pre>
     * In general, you can use this property to control class/resource loading priority for Mule contrainer classloader
     * for any dependency, not only for Mule patches.
     *
     * @param patches
     *     the patches
     * @return this for chaining
     */
    public MuleConfigProperties setPatches(List<String> patches) {
        this.patches = patches;
        return this;
    }

    /**
     * Gets server plugins.
     *
     * @return the server plugins
     */
    public List<Resource> getServerPlugins() {
        return serverPlugins;
    }

    /**
     * Sets server plugins.
     *
     * @param serverPlugins
     *     the server plugins
     * @return this for chaining
     */
    public MuleConfigProperties setServerPlugins(List<Resource> serverPlugins) {
        this.serverPlugins = serverPlugins;
        return this;
    }

    /**
     * Is auto load patches boolean.
     *
     * @return the boolean
     */
    public boolean isAutoLoadPatches() {
        return autoLoadPatches;
    }

    /**
     * Sets auto load patches.
     *
     * @param autoLoadPatches
     *     the auto load patches
     * @return this for chaining
     */
    public MuleConfigProperties setAutoLoadPatches(boolean autoLoadPatches) {
        this.autoLoadPatches = autoLoadPatches;
        return this;
    }

}
