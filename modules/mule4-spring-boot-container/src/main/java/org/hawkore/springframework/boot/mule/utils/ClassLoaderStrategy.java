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
package org.hawkore.springframework.boot.mule.utils;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

/**
 * The interface Class loader strategy.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public interface ClassLoaderStrategy {

    /**
     * Returns the class if the classloader is able to load it, null in other case
     *
     * @param cl
     *     the classLoader
     * @param className
     *     the class name
     * @return class
     */
    Class findClass(ClassLoader cl, String className);

    /**
     * Find resource.
     *
     * @param cl
     *     the classLoader
     * @param name
     *     the name
     * @return the url
     */
    URL findResource(ClassLoader cl, String name);

    /**
     * Find resources.
     *
     * @param cl
     *     the classLoader
     * @param name
     *     the name
     * @return the set
     * @throws IOException
     *     the io exception
     */
    Set<URL> findResources(ClassLoader cl, String name) throws IOException;

}
