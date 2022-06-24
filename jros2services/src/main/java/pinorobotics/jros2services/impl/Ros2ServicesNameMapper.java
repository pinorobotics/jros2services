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
package pinorobotics.jros2services.impl;

import id.jrosclient.utils.RosNameUtils;
import id.jrosmessages.Message;

/**
 * Mapper for ROS service names.
 *
 * <p>ROS naming differs between ROS versions
 * https://design.ros2.org/articles/topic_and_service_names.html on top of that it differs between
 * the ROS topics, services, actions, etc.
 *
 * @author lambdaprime intid@protonmail.com
 */
public class Ros2ServicesNameMapper {

    private static final String SERVICE_REQUEST = "ServiceRequest";
    private static final String SERICE_RESPONSE = "ServiceResponse";
    private RosNameUtils rosNameUtils;

    public Ros2ServicesNameMapper(RosNameUtils rosNameUtils) {
        this.rosNameUtils = rosNameUtils;
    }

    /**
     * Returns DDS topic name
     *
     * @see <a
     *     href="https://design.ros2.org/articles/topic_and_service_names.html#ros-specific-namespace-prefix">Topic
     *     and Service name mapping to DDS</a>
     */
    public <M extends Message> String asFullyQualifiedServiceName(
            Class<M> messageClass, String topicName) {
        topicName = rosNameUtils.toAbsoluteName(topicName);
        var path = rosNameUtils.getMessageName(messageClass);
        var name = path.getName(1).toString();
        if (name.endsWith(SERVICE_REQUEST)) return "rq" + topicName + "Request";
        if (name.endsWith(SERICE_RESPONSE)) return "rr" + topicName + "Reply";
        else
            throw new UnsupportedOperationException(
                    "Message name " + path + " should end with proper postfix");
    }

    /**
     * Unfortunately message type conversion between ROS and DDS is not documented yet. May be it is
     * intentionally to keep it as implementation detail. Here we construct message type name solely
     * based on empirical data based on how ROS does it.
     */
    public String asFullyQualifiedServiceMessageName(Class<? extends Message> messageClass) {
        var path = rosNameUtils.getMessageName(messageClass);
        var name = path.getName(1).toString();
        if (name.endsWith(SERVICE_REQUEST)) name = name.replace(SERVICE_REQUEST, "_Request_");
        else if (name.endsWith(SERICE_RESPONSE)) name = name.replace(SERICE_RESPONSE, "_Response_");
        else
            throw new UnsupportedOperationException(
                    "Message name " + path + " should end with proper postfix");
        return String.format("%s::srv::dds_::%s", path.getName(0), name);
    }
}
