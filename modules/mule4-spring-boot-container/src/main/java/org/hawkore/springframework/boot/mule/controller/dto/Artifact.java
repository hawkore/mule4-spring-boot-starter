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
package org.hawkore.springframework.boot.mule.controller.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.StringJoiner;

import org.mule.runtime.deployment.model.api.application.ApplicationStatus;

/**
 * Abstract Mule Artifact
 *
 * @param <T>
 *     the type parameter
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public abstract class Artifact<T extends Artifact> {

    private String name;
    private ApplicationStatus status;
    private long lastModified;

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets name.
     *
     * @param name
     *     the name
     * @return this for chaining
     */
    public T setName(String name) {
        this.name = name;
        return (T)this;
    }

    /**
     * Is deployed.
     *
     * @return the boolean
     */
    public boolean isDeployed() {
        return ApplicationStatus.STARTED.equals(status);
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public ApplicationStatus getStatus() {
        return status;
    }

    /**
     * Sets status.
     *
     * @param status
     *     the status
     * @return this for chaining
     */
    public Artifact<T> setStatus(ApplicationStatus status) {
        this.status = status;
        return this;
    }

    /**
     * A long value representing the time the file was last modified, measured in milliseconds since the epoch (00:00:00
     * GMT, January 1, 1970), or 0L if the file does not exist or if an I/O error occurs
     *
     * @return last modified
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Sets last modified.
     *
     * @param lastModified
     *     the last modified
     * @return this for chaining
     */
    public T setLastModified(long lastModified) {
        this.lastModified = lastModified;
        return (T)this;
    }

    /**
     * Gets last modified utc date time.
     *
     * @return the last modified utc date time
     */
    public String getLastModifiedUTCDateTime() {
        return LocalDateTime.ofEpochSecond(lastModified / 1000, 0, ZoneOffset.UTC).toString();
    }

    /**
     * Equals boolean.
     *
     * @param o
     *     the o
     * @return the boolean
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Artifact)) {
            return false;
        }
        Artifact<?> artifact = (Artifact<?>)o;
        return lastModified == artifact.lastModified && Objects.equals(name, artifact.name)
                   && status == artifact.status;
    }

    /**
     * Hash code int.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, status, lastModified);
    }

    /**
     * To string string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return new StringJoiner(", ", this.getClass().getSimpleName() + "[", "]").add("name='" + name + "'")
                   .add("status=" + status).add("lastModified=" + lastModified).add("deployed=" + isDeployed())
                   .add("lastModifiedUTCDateTime='" + getLastModifiedUTCDateTime() + "'").toString();
    }

}
