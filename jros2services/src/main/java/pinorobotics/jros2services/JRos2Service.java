/*
 * Copyright 2024 jrosservices project
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

import id.jrosmessages.Message;

/**
 * ROS2 Service
 *
 * @see <a
 *     href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 *     Services</a>
 * @see JRos2ServicesFactory Factory for available ROS2 Service implementations
 * @param <R> service request message type
 * @param <A> service response message type
 * @author lambdaprime intid@protonmail.com
 */
public interface JRos2Service<R extends Message, A extends Message> extends AutoCloseable {

    /**
     * Start ROS service
     *
     * <p>Once started, service becomes active and accepts the requests until it is closed with
     * {@link #close()}
     */
    void start();

    /** Stop ROS service */
    @Override
    void close();
}
