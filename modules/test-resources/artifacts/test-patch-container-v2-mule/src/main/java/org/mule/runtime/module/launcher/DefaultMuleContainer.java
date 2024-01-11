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
package org.mule.runtime.module.launcher;

import org.mule.runtime.module.deployment.api.DeploymentService;
import org.mule.runtime.module.extension.internal.loader.ExtensionModelLoaderManager;
import org.mule.runtime.module.launcher.coreextension.MuleCoreExtensionManagerServer;
import org.mule.runtime.module.repository.api.RepositoryService;
import org.mule.runtime.module.service.api.manager.ServiceManager;
import org.mule.runtime.module.tooling.api.ToolingService;

/**
 * Dummy class just for testing purposes of SpringMuleContainerV2Impl
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class DefaultMuleContainer extends org.mule.runtime.module.launcher.MuleContainer {

    public DefaultMuleContainer(String[] args) {
        super(args);
    }

    public DefaultMuleContainer(DeploymentService deploymentService,
        RepositoryService repositoryService,
        ToolingService toolingService,
        MuleCoreExtensionManagerServer coreExtensionManager,
        ServiceManager serviceManager,
        ExtensionModelLoaderManager extensionModelLoaderManager) {
        super(deploymentService, repositoryService, toolingService, coreExtensionManager, serviceManager,
            extensionModelLoaderManager);
    }

    public DefaultMuleContainer(String[] args,
        DeploymentService deploymentService,
        RepositoryService repositoryService,
        ToolingService toolingService,
        MuleCoreExtensionManagerServer coreExtensionManager,
        ServiceManager serviceManager,
        ExtensionModelLoaderManager extensionModelLoaderManager) throws IllegalArgumentException {
        super(args, deploymentService, repositoryService, toolingService, coreExtensionManager, serviceManager,
            extensionModelLoaderManager);
    }

}
