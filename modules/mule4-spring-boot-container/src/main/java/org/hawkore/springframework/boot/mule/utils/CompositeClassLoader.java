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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Composite classloader that delegates classloading in the first delegated classloader that was able to load a
 * class/resource, starting on parent classloader.
 * <p>
 * Set a {@link ClassLoaderStrategy} to override default one that just iterate over each delegated
 * classloader calling "loadClass" method.
 *
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
public class CompositeClassLoader extends ClassLoader {

    private final ClassLoaderStrategy strategy;
    private final ClassLoader parent;
    private final ClassLoader[] childClassLoaders;
    private final Predicate<String> inclusionClassFilter;
    private final Predicate<String> exclusionClassFilter;
    private final Predicate<String> inclusionResourceFilter;
    private final Predicate<String> exclusionResourceFilter;

    /**
     * Instantiates a new Composite class loader.
     *
     * @param parent
     *     the parent
     * @param childClassLoaders
     *     the child class loaders
     */
    public CompositeClassLoader(final ClassLoader parent, ClassLoader... childClassLoaders) {
        this(parent, null, null, null, null, null, childClassLoaders);
    }

    /**
     * Instantiates a new Composite class loader.
     *
     * @param parent
     *     the parent
     * @param inclusionClassFilter
     *     the inclusion class filter
     * @param exclusionClassFilter
     *     the exclusion class filter
     * @param inclusionResourceFilter
     *     the inclusion resource filter
     * @param exclusionResourceFilter
     *     the exclusion resource filter
     * @param childClassLoaders
     *     the child class loaders
     */
    public CompositeClassLoader(final ClassLoader parent,
        Predicate<String> inclusionClassFilter,
        Predicate<String> exclusionClassFilter,
        Predicate<String> inclusionResourceFilter,
        Predicate<String> exclusionResourceFilter,
        ClassLoader... childClassLoaders) {
        this(parent, null, inclusionClassFilter, exclusionClassFilter, inclusionResourceFilter, exclusionResourceFilter,
            childClassLoaders);
    }

    /**
     * Instantiates a new Composite class loader.
     *
     * @param parent
     *     the parent
     * @param strategy
     *     the strategy
     * @param childClassLoaders
     *     the child class loaders
     */
    public CompositeClassLoader(final ClassLoader parent,
        ClassLoaderStrategy strategy,
        ClassLoader... childClassLoaders) {
        this(parent, strategy, null, null, null, null, childClassLoaders);
    }

    /**
     * Instantiates a new Composite class loader.
     *
     * @param parent
     *     the parent
     * @param strategy
     *     the strategy
     * @param inclusionClassFilter
     *     the inclusion class filter
     * @param exclusionClassFilter
     *     the exclusion class filter
     * @param inclusionResourceFilter
     *     the inclusion resource filter
     * @param exclusionResourceFilter
     *     the exclusion resource filter
     * @param childClassLoaders
     *     the child class loaders
     */
    public CompositeClassLoader(final ClassLoader parent,
        ClassLoaderStrategy strategy,
        Predicate<String> inclusionClassFilter,
        Predicate<String> exclusionClassFilter,
        Predicate<String> inclusionResourceFilter,
        Predicate<String> exclusionResourceFilter,
        ClassLoader... childClassLoaders) {
        super(parent);
        this.parent = parent;
        if (childClassLoaders == null) {
            this.childClassLoaders = new ClassLoader[0];
        } else {
            this.childClassLoaders = childClassLoaders;
        }
        this.inclusionClassFilter = inclusionClassFilter == null ? s -> true : inclusionClassFilter;
        this.exclusionClassFilter = exclusionClassFilter == null ? s -> false : exclusionClassFilter;
        this.inclusionResourceFilter = inclusionResourceFilter == null ? s -> true : inclusionResourceFilter;
        this.exclusionResourceFilter = exclusionResourceFilter == null ? s -> false : exclusionResourceFilter;
        this.strategy = strategy == null ? new DefaultStrategy(this.inclusionClassFilter, this.exclusionClassFilter,
            this.inclusionResourceFilter, this.exclusionResourceFilter) : strategy;
    }

    /**
     * Load class class.
     *
     * @param name
     *     the name
     * @return the class
     * @throws ClassNotFoundException
     *     the class not found exception
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> c = strategy.findClass(parent, name);
        if (c == null) {
            for (ClassLoader cl : childClassLoaders) {
                c = strategy.findClass(cl, name);
                if (c != null) {
                    return c;
                }
            }
        }
        throw new ClassNotFoundException("Class <" + name + "> not found in any classloader");
    }

    /**
     * Gets resource.
     *
     * @param name
     *     the name
     * @return the resource
     */
    @Override
    public URL getResource(String name) {
        URL c = strategy.findResource(parent, name);
        if (c == null) {
            for (ClassLoader cl : childClassLoaders) {
                c = strategy.findResource(cl, name);
                if (c != null) {
                    return c;
                }
            }
        }
        return c;
    }

    /**
     * Gets resources.
     *
     * @param name
     *     the name
     * @return the resources
     * @throws IOException
     *     the io exception
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> resources = strategy.findResources(parent, name); //NOSONAR
        for (ClassLoader cl : childClassLoaders) {
            resources.addAll(strategy.findResources(cl, name));
        }
        return Collections.enumeration(resources);
    }

    // Default strategy

    public static class DefaultStrategy implements ClassLoaderStrategy {

        private final Predicate<String> inclusionClassFilter;
        private final Predicate<String> exclusionClassFilter;
        private final Predicate<String> inclusionResourceFilter;
        private final Predicate<String> exclusionResourceFilter;

        /**
         * Instantiates a new Default strategy.
         *
         * @param inclusionClassFilter
         *     the inclusion class filter
         * @param exclusionClassFilter
         *     the exclusion class filter
         * @param inclusionResourceFilter
         *     the inclusion resource filter
         * @param exclusionResourceFilter
         *     the exclusion resource filter
         */
        public DefaultStrategy(Predicate<String> inclusionClassFilter,
            Predicate<String> exclusionClassFilter,
            Predicate<String> inclusionResourceFilter,
            Predicate<String> exclusionResourceFilter) {
            this.inclusionClassFilter = inclusionClassFilter;
            this.exclusionClassFilter = exclusionClassFilter;
            this.inclusionResourceFilter = inclusionResourceFilter;
            this.exclusionResourceFilter = exclusionResourceFilter;
        }

        /**
         * Find class.
         *
         * @param cl
         *     the classLoader
         * @param className
         *     the class name
         * @return the class
         */
        @Override
        public Class<?> findClass(ClassLoader cl, String className) {
            try {
                if (!inclusionClassFilter.test(className) || exclusionClassFilter.test(className)) {
                    return null;
                }
                return cl.loadClass(className);
            } catch (Exception e) {
                // this catch is required, as we need to iterate over all provided classloaders within our composite
                // classloader
                return null;
            }
        }

        /**
         * Find resource.
         *
         * @param cl
         *     the classLoader
         * @param name
         *     the name
         * @return the url
         */
        @Override
        public URL findResource(ClassLoader cl, String name) {
            try {
                if (!inclusionResourceFilter.test(name) || exclusionResourceFilter.test(name)) {
                    return null;
                }
                return cl.getResource(name);
            } catch (Exception e) {
                // this catch is required, as we need to iterate over all provided classloaders within our composite
                // classloader
                return null;
            }
        }

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
        @Override
        public Set<URL> findResources(ClassLoader cl, String name) throws IOException {
            try {
                if (!inclusionResourceFilter.test(name) || exclusionResourceFilter.test(name)) {
                    return Collections.emptySet();
                }
                HashSet<URL> resources = new HashSet<>(); //NOSONAR
                Enumeration<URL> c = cl.getResources(name); //NOSONAR
                while (c.hasMoreElements()) {
                    resources.add(c.nextElement());
                }
                return resources;
            } catch (Exception e) {
                // this catch is required, as we need to iterate over all provided classloaders within our composite
                // classloader
                return Collections.emptySet();
            }
        }

    }

}
