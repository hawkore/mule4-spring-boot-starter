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

import org.hawkore.springframework.boot.mule.container.SpringMuleContainer;
import org.hawkore.springframework.boot.mule.health.MuleRuntimeHealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

/**
 * Mule Container configuration
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@Configuration
@ComponentScan("org.hawkore.springframework.boot.mule.container")
@Import({MuleConfigProperties.class})
public class MuleContainerConfiguration {

    /**
     * Health indicator for Mule runtime.
     *
     * @param muleContainer
     *     the mule container
     * @return the health indicator
     */
    @Bean
    @DependsOn("SpringMuleContainer")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public HealthIndicator muleRuntimeHealth(@Autowired SpringMuleContainer muleContainer) {
        return new MuleRuntimeHealthIndicator(muleContainer);
    }

}
