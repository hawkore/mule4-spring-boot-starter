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

import org.hawkore.springframework.boot.mule.container.v2.SpringBootMule4RuntimeStopStartNOCleanStartupTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author Manuel Núñez Sánchez (manuel.nunez@hawkore.com)
 */
@RunWith(Suite.class)
@SuiteClasses({SpringBootMule4RuntimeStopStartNOCleanStartupTests.class})
public class StopStartUnitTestSuite {}
