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

import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * SpringMuleContainer contract
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public interface SpringMuleContainer extends ApplicationListener<ApplicationEvent> {

    /**
     * Whether application is deployed and running.
     *
     * @param application
     *     the application
     * @return the boolean
     */
    boolean isApplicationDeployed(String application);

    /**
     * Whether domain is deployed and running.
     *
     * @param domain
     *     the domain
     * @return the boolean
     */
    boolean isDomainDeployed(String domain);

    /**
     * Whether application is installed (application directory exists).
     *
     * @param application
     *     the application
     * @return the boolean
     */
    boolean isApplicationInstalled(String application);

    /**
     * Whether domain is installed (domain directory exists).
     *
     * @param domain
     *     the domain
     * @return the boolean
     */
    boolean isDomainInstalled(String domain);

    /**
     * Gets installed applications.
     *
     * @return the applications
     */
    List<Application> getApplications();

    /**
     * Gets installed domains.
     *
     * @return the domains
     */
    List<Domain> getDomains();

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
    void deployApplication(File appFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled);

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
    void deployDomain(File domainFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled);

    /**
     * Undeploy application.
     *
     * @param application
     *     the application
     * @throws IOException
     *     the io exception
     */
    void undeployApplication(String application) throws IOException;

    /**
     * Undeploy domain.
     *
     * @param domain
     *     the domain
     * @throws IOException
     *     the io exception
     */
    void undeployDomain(String domain) throws IOException;

}
