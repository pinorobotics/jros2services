/*
 * Copyright 2022 jrosservices project
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import pinorobotics.jrosservices.msgs.ServiceDefinition;

/**
 * Factory methods of <b>jros2services</b> module.
 *
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServicesFactory {

    private DdsNameMapper nameMapper = new DdsNameMapper(new RosNameUtils());

    /**
     * Create ROS2 Service client
     *
     * @param client ROS2 client
     * @param serviceDefinition type definitions for a service messages
     * @param serviceName name of the ROS2 service to which client will send the requests for
     *     execution
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

    /**
     * Create ROS2 Service with {@link Executors#newCachedThreadPool()} as default executor
     *
     * @see #createService(JRos2Client, ServiceDefinition, String, Function, ExecutorService)
     */
    public <R extends Message, A extends Message> JRos2Service<R, A> createService(
            JRos2Client client,
            ServiceDefinition<R, A> serviceDefinition,
            String serviceName,
            Function<R, A> handler) {
        return createService(
                client, serviceDefinition, serviceName, Executors.newCachedThreadPool(), handler);
    }

    /**
     * Create ROS2 Service
     *
     * <p>Service needs to be started explicitly with {@link JRos2Service#start()}.
     *
     * @param client ROS2 client
     * @param serviceDefinition type definitions for a service messages
     * @param serviceName name of the ROS2 service
     * @param handler function which will process all incoming service requests
     * @param <R> request message type
     * @param <A> response message type
     */
    public <R extends Message, A extends Message> JRos2Service<R, A> createService(
            JRos2Client client,
            ServiceDefinition<R, A> serviceDefinition,
            String serviceName,
            ExecutorService executor,
            Function<R, A> handler) {
        if (client instanceof JRos2ClientImpl ros2Client) {
            return new JRos2Service<>(
                    ros2Client.getRtpsTalkClient(),
                    serviceDefinition,
                    serviceName,
                    nameMapper,
                    executor,
                    handler);
        } else {
            throw new IllegalArgumentException("Unknown JRos2Client implementation");
        }
    }
}
