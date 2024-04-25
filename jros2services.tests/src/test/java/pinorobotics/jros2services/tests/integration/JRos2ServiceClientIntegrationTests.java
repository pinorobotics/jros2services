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
package pinorobotics.jros2services.tests.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import id.jros2client.JRos2Client;
import id.jros2client.JRos2ClientFactory;
import id.opentelemetry.exporters.extensions.ElasticsearchMetricsExtension;
import id.xfunction.lang.XExec;
import id.xfunction.lang.XProcess;
import id.xfunction.logging.XLogger;
import java.net.MalformedURLException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import pinorobotics.jros2services.JRos2ServiceClient;
import pinorobotics.jros2services.JRos2ServiceClientFactory;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsRequestMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsResponseMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsServiceDefinition;

/**
 * @author lambdaprime intid@protonmail.com
 */
@ExtendWith({ElasticsearchMetricsExtension.class})
public class JRos2ServiceClientIntegrationTests {

    private JRos2Client client;
    private JRos2ServiceClient<AddTwoIntsRequestMessage, AddTwoIntsResponseMessage> serviceClient;
    private XProcess service;

    @BeforeAll
    public static void setupAll() {
        XLogger.load("jros2services-test.properties");
    }

    @BeforeEach
    public void setup() throws MalformedURLException {
        service =
                new XExec("ws2/build/examples_rclcpp_minimal_service/service_main")
                        .start()
                        .forwardOutputAsync(true);
        client = new JRos2ClientFactory().createClient();
        serviceClient =
                new JRos2ServiceClientFactory()
                        .createClient(client, new AddTwoIntsServiceDefinition(), "add_two_ints");
    }

    @AfterEach
    public void clean() throws Exception {
        serviceClient.close();
        client.close();
        service.destroyAllForcibly();
    }

    @Test
    public void test_sendRequest() throws Exception {
        var seed = 63;
        var pendingResults =
                IntStream.range(0, 5)
                        .mapToObj(i -> new AddTwoIntsRequestMessage(seed, i))
                        .map(req -> serviceClient.sendRequestAsync(req))
                        .toList();
        for (int i = 0; i < pendingResults.size(); i++) {
            var result = pendingResults.get(i).get();
            System.out.println(result);
            assertEquals(seed + i, result.sum);
        }
    }
}
