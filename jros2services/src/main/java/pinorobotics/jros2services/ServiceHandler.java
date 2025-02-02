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
 * Service handler is responsible for executing all incoming ROS2 requests.
 *
 * <p>Supposed to be implemented by the users.
 *
 * @author lambdaprime intid@protonmail.com
 */
@FunctionalInterface
public interface ServiceHandler<R extends Message, A extends Message> {
    A execute(R request) throws Exception;
}
