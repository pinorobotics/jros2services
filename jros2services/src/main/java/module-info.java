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
/**
 * Java module which allows to interact with <a
 * href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 * (Robot Operating System) Services</a>.
 *
 * <p>Currently there is no document which would describe ROS2 Service communication, as the result
 * different ROS 2 Middleware Layers (RMW) <a
 * href="https://github.com/ros2/rmw_cyclonedds/issues/184">implement it differently</a>.
 *
 * <p>As of now this client supports only Fast-DDS RML implementation since it was default one in
 * ROS2 Foxy.
 *
 * <p>In future it is possible that all RMW implementations will switch to DDS-RPC standard (see <a
 * href="https://design.ros2.org/articles/ros_on_dds.html">Services and Actions</a>).
 *
 * <p>For usage examples see <a href="http://pinoweb.freetzi.com/jrosservices">Documentation</a>
 *
 * @see <a href="https://github.com/pinorobotics/jros2services">GitHub repository</a>
 * @see <a
 *     href="https://github.com/pinorobotics/jros2services/blob/main/jros2services/release/CHANGELOG.md">Releases</a>
 * @see <a
 *     href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 *     Services</a>
 * @author lambdaprime intid@protonmail.com
 */
module jros2services {
    requires transitive jrosservices;
    requires transitive jros2messages;
    requires transitive jros2client;
    requires jrosclient;
    requires rtpstalk;
    requires id.xfunction;
    requires jrosmessages;
    requires io.opentelemetry.api;

    exports pinorobotics.jros2services;
}
