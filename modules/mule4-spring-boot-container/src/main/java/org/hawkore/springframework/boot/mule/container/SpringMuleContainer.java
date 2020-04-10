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
import java.util.List;

import org.hawkore.springframework.boot.mule.controller.dto.Application;
import org.hawkore.springframework.boot.mule.controller.dto.Domain;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * SpringMuleContainer contract
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public interface SpringMuleContainer extends ApplicationListener<ApplicationEvent>, InitializingBean {

    boolean isApplicationDeployed(String application);

    boolean isDomainDeployed(String domain);

    List<Application> getApplications();

    List<Domain> getDomains();

    void deployApplication(File appFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled);

    void deployDomain(File domainFile,
        Boolean lazyInitializationEnabled,
        Boolean xmlValidationsEnabled,
        Boolean lazyConnectionsEnabled);

    void undeployApplication(String application);

    void undeployDomain(String domain);

}
