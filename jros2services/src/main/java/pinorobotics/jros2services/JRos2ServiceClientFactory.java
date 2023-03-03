/*
 * Copyright 2022 jros2services project
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
package pinorobotics.jros2services;

import id.jros2client.JRos2Client;
import id.jros2client.impl.JRos2ClientImpl;
import id.jros2client.impl.rmw.DdsNameMapper;
import id.jrosclient.utils.RosNameUtils;
import id.jrosmessages.Message;
import pinorobotics.jrosservices.msgs.ServiceDefinition;

/**
 * Factory methods to create {@link JRos2ServiceClient}
 *
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServiceClientFactory {

    private DdsNameMapper nameMapper = new DdsNameMapper(new RosNameUtils());

    /**
     * Create client for ROS2 Services
     *
     * @param client ROS client
     * @param serviceDefinition message type definitions for an service
     * @param serviceName name of the service which will execute the requests
     * @param <R> request message type
     * @param <A> response message type
     */
    public <R extends Message, A extends Message> JRos2ServiceClient<R, A> createClient(
            JRos2Client client, ServiceDefinition<R, A> serviceDefinition, String serviceName) {
        if (client instanceof JRos2ClientImpl ros2Client) {
            return new JRos2ServiceClient<>(
                    ros2Client.getRtpsTalkClient(), serviceDefinition, serviceName, nameMapper);
        } else {
            throw new IllegalArgumentException("Unknown JRos2Client implementation");
        }
    }
}
