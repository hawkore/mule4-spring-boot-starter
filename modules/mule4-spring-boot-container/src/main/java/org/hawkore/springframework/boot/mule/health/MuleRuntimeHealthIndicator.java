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
package org.hawkore.springframework.boot.mule.health;

import org.hawkore.springframework.boot.mule.container.SpringMuleContainer;
import org.mule.runtime.core.api.config.MuleManifest;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * Mule Runtime health indicator
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class MuleRuntimeHealthIndicator extends AbstractHealthIndicator {

    private final SpringMuleContainer muleContainer;

    /**
     * Instantiates a new Mule runtime health indicator.
     *
     * @param muleContainer the mule container
     */
    public MuleRuntimeHealthIndicator(SpringMuleContainer muleContainer) {
        super();
        this.muleContainer = muleContainer;
    }

    /**
     * Do health check.
     *
     * @param builder the builder
     * @throws Exception the exception
     */
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (!muleContainer.isRunning()) {
            builder.down();
            return;
        }

        boolean failed = muleContainer.getApplications().stream().anyMatch(a -> !a.isDeployed()) ||
            //domains
            muleContainer.getDomains().stream().anyMatch(a -> !a.isDeployed());

        if (failed) {
            builder.outOfService();
        }
        else {
            builder.up();
        }

        builder.withDetail("Version:", MuleManifest.getProductName() + " v" + MuleManifest.getProductVersion());
        builder.withDetail("Build:", MuleManifest.getBuildNumber());

        muleContainer.getDomains().stream()
            .forEach(a -> builder.withDetail("DOMAIN: " + a.getName(), a.getStatus()));

        muleContainer.getApplications().stream()
            .forEach(a -> builder.withDetail("APP: " + a.getName(), a.getStatus()));
    }

}
