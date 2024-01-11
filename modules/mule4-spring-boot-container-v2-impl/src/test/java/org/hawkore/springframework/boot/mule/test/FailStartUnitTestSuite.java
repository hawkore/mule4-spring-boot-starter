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
package org.hawkore.springframework.boot.mule.test;

import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup1Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup2Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup3Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup4Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup5Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup6Tests;
import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4FailStartup7Tests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@RunWith(Suite.class)
@SuiteClasses({SpringBootMule4FailStartup1Tests.class,
    SpringBootMule4FailStartup2Tests.class,
    SpringBootMule4FailStartup3Tests.class, SpringBootMule4FailStartup4Tests.class,
    SpringBootMule4FailStartup5Tests.class, SpringBootMule4FailStartup6Tests.class,
    SpringBootMule4FailStartup7Tests.class
        })
public class FailStartUnitTestSuite {}
