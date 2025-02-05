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
import id.jroscommon.RosName;
import id.jrosmessages.Message;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import pinorobotics.jros2services.impl.JRos2ServiceClientImpl;
import pinorobotics.jros2services.impl.JRos2ServiceImpl;
import pinorobotics.jrosservices.JRosServiceClient;
import pinorobotics.jrosservices.msgs.ServiceDefinition;

/**
 * Factory methods of <b>jros2services</b> module.
 *
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServicesFactory {

    private DdsNameMapper nameMapper = new DdsNameMapper();

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
    public <R extends Message, A extends Message> JRosServiceClient<R, A> createClient(
            JRos2Client client, ServiceDefinition<R, A> serviceDefinition, String serviceName) {
        if (client instanceof JRos2ClientImpl ros2Client) {
            return new JRos2ServiceClientImpl<>(
                    ros2Client.getRtpsTalkClient(),
                    serviceDefinition,
                    new RosName(serviceName),
                    nameMapper);
        } else {
            throw new IllegalArgumentException("Unknown JRos2Client implementation");
        }
    }

    /**
     * Simplified version of {@link #createService(JRos2Client, ServiceDefinition, RosName,
     * ServiceHandler)} where service name is converted to {@link RosName}
     */
    public <R extends Message, A extends Message> JRos2Service<R, A> createService(
            JRos2Client client,
            ServiceDefinition<R, A> serviceDefinition,
            String serviceName,
            ServiceHandler<R, A> handler) {
        return createService(
                client,
                serviceDefinition,
                new RosName(serviceName),
                Executors.newCachedThreadPool(),
                handler);
    }

    /**
     * Create ROS2 Service with {@link Executors#newCachedThreadPool()} as default executor
     *
     * @see JRos2ServicesFactory#createService(JRos2Client, ServiceDefinition, RosName,
     *     ExecutorService, ServiceHandler)
     */
    public <R extends Message, A extends Message> JRos2Service<R, A> createService(
            JRos2Client client,
            ServiceDefinition<R, A> serviceDefinition,
            RosName serviceName,
            ServiceHandler<R, A> handler) {
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
     * @param handler service handler to process all incoming ROS service requests
     * @param <R> request message type
     * @param <A> response message type
     */
    public <R extends Message, A extends Message> JRos2Service<R, A> createService(
            JRos2Client client,
            ServiceDefinition<R, A> serviceDefinition,
            RosName serviceName,
            ExecutorService executor,
            ServiceHandler<R, A> handler) {
        if (client instanceof JRos2ClientImpl ros2Client) {
            return new JRos2ServiceImpl<>(
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
