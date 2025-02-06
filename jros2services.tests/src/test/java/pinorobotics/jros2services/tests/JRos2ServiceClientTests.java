/*
 * Copyright 2025 jrosservices project
 * 
 * Website: https://github.com/pinorobotics/jros2services
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pinorobotics.jros2services.tests;

import id.jros2client.JRos2ClientConfiguration;
import id.jros2client.impl.JRos2ClientImpl;
import id.jros2client.impl.ObjectsFactory;
import id.jros2client.impl.rmw.DdsNameMapper;
import id.jroscommon.RosName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import pinorobotics.jros2services.impl.JRos2ServiceClientImpl;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsServiceDefinition;

/**
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServiceClientTests {
    /**
     * It is important to start jrosclient so that it later could close properly and close rtps
     * client. If jrosclient is not started then close operation on it will be ignored
     */
    @Test
    public void test_ros_client_is_started() {
        var isStarted = new boolean[1];
        var mockJRosClient =
                new JRos2ClientImpl(
                        new JRos2ClientConfiguration.Builder().build(), new ObjectsFactory()) {
                    @Override
                    public void start() {
                        isStarted[0] = true;
                    }
                };
        try (var client =
                new JRos2ServiceClientImpl<>(
                        mockJRosClient,
                        new AddTwoIntsServiceDefinition(),
                        new RosName("serviceHello"),
                        new DdsNameMapper())) {
            client.start();
        }
        Assertions.assertEquals(true, isStarted[0]);
    }
}
