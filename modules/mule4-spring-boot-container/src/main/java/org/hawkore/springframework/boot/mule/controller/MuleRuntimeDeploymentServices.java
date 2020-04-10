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
package org.hawkore.springframework.boot.mule.controller;

import java.io.File;

import org.hawkore.springframework.boot.mule.container.SpringMuleContainer;
import org.hawkore.springframework.boot.mule.controller.dto.ErrorMessage;
import org.hawkore.springframework.boot.mule.utils.StorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Simple REST controller for Mule Runtime Deployment Services
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@RestController
@RequestMapping(value = "mule")
@ConditionalOnWebApplication
public class MuleRuntimeDeploymentServices {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private SpringMuleContainer muleContainer;

    /**
     * List Mule domains.
     *
     * @return the domains
     */
    @GetMapping(value = "/domains", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getDomains() {
        try {
            return ResponseEntity.ok(muleContainer.getDomains());
        } catch (Exception e) {
            logger.error("Error retrieving domains", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

    /**
     * List Mule applications.
     *
     * @return the apps
     */
    @GetMapping(value = "/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getApps() {
        try {
            return ResponseEntity.ok(muleContainer.getApplications());
        } catch (Exception e) {
            logger.error("Error retrieving applications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

    /**
     * Deploy Mule application.
     *
     * @param app
     *     the app
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled flag
     * @param xmlValidationsEnabled
     *     the xml validations enabled flag
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled flag
     * @return List of Mule applications
     */
    @PostMapping(value = "/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> deployApp(@RequestParam("file") MultipartFile app,
        @RequestParam(name = "lazyInitializationEnabled", required = false) Boolean lazyInitializationEnabled,
        @RequestParam(name = "xmlValidationsEnabled", required = false) Boolean xmlValidationsEnabled,
        @RequestParam(name = "lazyConnectionsEnabled", required = false) Boolean lazyConnectionsEnabled) {
        File artifact = null;
        try {
            artifact = StorageUtils.storeArtifactTemp(app);
            muleContainer
                .deployApplication(artifact, lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
            return ResponseEntity.ok(muleContainer.getApplications());
        } catch (Exception e) {
            logger.error("Error deploying application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

    /**
     * Deploy Mule domain.
     *
     * @param domain
     *     the domain
     * @param lazyInitializationEnabled
     *     the lazy initialization enabled
     * @param xmlValidationsEnabled
     *     the xml validations enabled
     * @param lazyConnectionsEnabled
     *     the lazy connections enabled
     * @return List of Mule domains
     */
    @PostMapping(value = "/domains", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> deployDomain(@RequestParam("file") MultipartFile domain,
        @RequestParam(name = "lazyInitializationEnabled", required = false) Boolean lazyInitializationEnabled,
        @RequestParam(name = "xmlValidationsEnabled", required = false) Boolean xmlValidationsEnabled,
        @RequestParam(name = "lazyConnectionsEnabled", required = false) Boolean lazyConnectionsEnabled) {
        File artifact = null;
        try {
            artifact = StorageUtils.storeArtifactTemp(domain);
            muleContainer
                .deployDomain(artifact, lazyInitializationEnabled, xmlValidationsEnabled, lazyConnectionsEnabled);
            return ResponseEntity.ok(muleContainer.getDomains());
        } catch (Exception e) {
            logger.error("Error deploying domain", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

    /**
     * Undeploy Mule application.
     *
     * @param app
     *     the application name
     * @return List of Mule Applications
     */
    @DeleteMapping(value = "/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> undeployApp(@RequestParam("name") String app) {
        try {
            muleContainer.undeployApplication(app);
            return ResponseEntity.ok(muleContainer.getApplications());
        } catch (Exception e) {
            logger.error("Error un-deploying application", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

    /**
     * Undeploy Mule domain.
     *
     * @param domain
     *     the domain name
     * @return List of Mule domains
     */
    @DeleteMapping(value = "/domains", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> undeployDomain(@RequestParam("name") String domain) {
        try {
            muleContainer.undeployDomain(domain);
            return ResponseEntity.ok(muleContainer.getDomains());
        } catch (Exception e) {
            logger.error("Error un-deploying domain", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .body(new ErrorMessage().setMessage(e.getMessage()));
        }
    }

}
