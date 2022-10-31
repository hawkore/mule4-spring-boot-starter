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
package org.hawkore.springframework.boot.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import de.codecentric.boot.admin.server.utils.jackson.AdminServerModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The type Common config.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@Configuration
public class CommonConfig {
    /**
     * Object mapper object mapper.
     *
     * @return the object mapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Register deserializer for Registration class into current ObjectMapper for Spring Boot Admin
     *
     * @param objectMapper the current object mapper
     * @return the object mapper
     */
    @Bean
    @Primary
    public ObjectMapper jacksonObjectMapper(@Autowired ObjectMapper objectMapper) {
        objectMapper.registerModule(new AdminServerModule(new String[] {".*password$"}));
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule()); // new module, NOT JSR310Module
        return objectMapper;
    }

}
