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

import id.jros2client.JRos2Client;
import id.jros2client.JRos2ClientConfiguration;
import id.jros2client.JRos2ClientFactory;
import id.opentelemetry.exporters.extensions.ElasticsearchMetricsExtension;
import id.xfunction.logging.XLogger;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pinorobotics.jros2services.JRos2ServicesFactory;
import pinorobotics.jros2services.ServiceHandler;
import pinorobotics.jros2services.tests.integration.ClientExecutor.Result;
import pinorobotics.jros2services.tests.integration.clientexecutors.JRos2ServiceClientExecutor;
import pinorobotics.jros2services.tests.integration.clientexecutors.ThirdpartyClientExecutor;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsRequestMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsResponseMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkConfiguration;

/**
 * @author lambdaprime intid@protonmail.com
 */
@ExtendWith({ElasticsearchMetricsExtension.class})
public class JRos2ServiceIntegrationTests {

    private static final String SERVICE_NAME = "jros_add_two_ints";
    private JRos2Client jrosClient;

    @BeforeAll
    public static void setupAll() {
        XLogger.load("jros2services-test.properties");
    }

    @BeforeEach
    public void setup() {
        jrosClient =
                new JRos2ClientFactory()
                        .createClient(
                                new JRos2ClientConfiguration.Builder()
                                        .rtpsTalkConfiguration(
                                                new RtpsTalkConfiguration.Builder()
                                                        .historyCacheMaxSize(1_000)
                                                        .publisherMaxBufferSize(1_000)
                                                        .build())
                                        .build());
    }

    @AfterEach
    public void clean() {
        jrosClient.close();
    }

    static Stream<ClientExecutor> dataProvider() {
        return Stream.of(
                new ThirdpartyClientExecutor(17, SERVICE_NAME),
                new JRos2ServiceClientExecutor(1, SERVICE_NAME));
    }

    @ParameterizedTest
    @MethodSource("dataProvider")
    public void test_happy(ClientExecutor clientExecutor) throws Exception {
        /* --------------------------------------------------------------
         * 1️⃣  Start the ROS‑2 service that the clients call.
         * -------------------------------------------------------------- */
        try (var service =
                new JRos2ServicesFactory()
                        .createService(
                                jrosClient,
                                new AddTwoIntsServiceDefinition(),
                                SERVICE_NAME,
                                this::proc)) {
            // Clients must wait for the service to be ready because of the QOS policies used by
            // default
            service.start();
            /* --------------------------------------------------------------
             * 2️⃣  Execute the third‑party client processes via the new
             *     {@link ClientExecutor}.  The method returns a list of
             *     {@link Future}s – each future completes with the captured log.
             * -------------------------------------------------------------- */
            List<Future<Result>> futures = clientExecutor.executeClientsAsync();

            /* --------------------------------------------------------------
             * 3️⃣  Wait for every client process to finish successfully.
             *     The call to {@code future.get()} will re‑throw if the
             *     process ended with a non‑zero exit code or if an I/O error
             *     occurred, which matches the original {@code assertEquals(0,
             *     proc.await())} behaviour.
             * -------------------------------------------------------------- */
            for (Future<Result> future : futures) {
                var result = future.get();
                String actualOutput = result.output();
                int seed = result.seed();
                Assertions.assertEquals(generateExpectedOutput(seed), actualOutput);
            }
        } finally {
            clientExecutor.close();
        }
    }

    private String generateExpectedOutput(int start) {
        return IntStream.range(1, 11)
                .mapToObj(i -> ": result of %d + %d = %d".formatted(start, i, start + i))
                .collect(Collectors.joining("\n"));
    }

    private AddTwoIntsResponseMessage proc(AddTwoIntsRequestMessage request) {
        return new AddTwoIntsResponseMessage(request.a + request.b);
    }

    public static void test_example_from_documentation() throws IOException {
        var clientFactory = new JRos2ClientFactory();
        var serviceClientFactory = new JRos2ServicesFactory();
        ServiceHandler<AddTwoIntsRequestMessage, AddTwoIntsResponseMessage> proc =
                request -> {
                    System.out.println("Received new request " + request);
                    var response = new AddTwoIntsResponseMessage(request.a + request.b);
                    System.out.println("Result " + response);
                    return response;
                };
        try (var client = clientFactory.createClient();
                var service =
                        serviceClientFactory.createService(
                                client,
                                new AddTwoIntsServiceDefinition(),
                                "jros_add_two_ints",
                                proc)) {
            service.start();
            System.out.println("Press Enter to stop ROS service...");
            System.in.read();
        }
    }

    public static void main(String[] args) throws IOException {
        test_example_from_documentation();
    }
}
