/*
 * Copyright 2021 jrosservices project
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
package pinorobotics.jros2services.tests.integration.example_interfaces_msgs;

import id.jrosmessages.Message;
import id.jrosmessages.MessageMetadata;
import id.jrosmessages.RosInterfaceType;
import id.xfunction.XJson;
import java.util.Objects;

/**
 * Definition for example_interfaces/AddTwoInts_Response
 *
 * @author lambdaprime intid@protonmail.com
 */
@MessageMetadata(name = AddTwoIntsResponseMessage.NAME, interfaceType = RosInterfaceType.SERVICE)
public class AddTwoIntsResponseMessage implements Message {

    static final String NAME = "example_interfaces/AddTwoIntsServiceResponse";

    public long sum;

    public AddTwoIntsResponseMessage() {}

    public AddTwoIntsResponseMessage(long sum) {
        this.sum = sum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sum);
    }

    @Override
    public boolean equals(Object obj) {
        var other = (AddTwoIntsResponseMessage) obj;
        return sum == other.sum;
    }

    @Override
    public String toString() {
        return XJson.asString("sum", sum);
    }
}
