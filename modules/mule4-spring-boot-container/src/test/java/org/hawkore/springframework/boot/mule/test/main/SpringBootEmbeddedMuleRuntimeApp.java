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
package org.hawkore.springframework.boot.mule.test.main;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.hawkore.springframework.boot.mule.config.EnableSpringMuleRuntimeDeploymentServices;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot Embedded Mule Runtime.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@EnableSpringMuleRuntimeDeploymentServices
@SpringBootApplication
public class SpringBootEmbeddedMuleRuntimeApp {

    /* trick for testing purposes - log levels code coverage */
    @ConditionalOnProperty("unit.test.log.package")
    @Bean
    public ch.qos.logback.classic.Logger logLevelUnitTests(
        @Value("${unit.test.log.package}") String unitTestsLogPackageName,
        @Value("${unit.test.log.level}") String unitTestsLogLevel) {
        LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(unitTestsLogPackageName);
        logger.setLevel(Level.toLevel(unitTestsLogLevel));
        return logger;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SpringBootEmbeddedMuleRuntimeApp.class);
        app.setBannerMode(Mode.OFF);
        app.run(args);
    }

}
