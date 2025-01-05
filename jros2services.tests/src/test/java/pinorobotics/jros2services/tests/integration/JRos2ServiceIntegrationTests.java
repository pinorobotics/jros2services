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
import id.xfunction.lang.XExec;
import id.xfunction.lang.XProcess;
import id.xfunction.logging.XLogger;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import pinorobotics.jros2services.JRos2ServicesFactory;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsRequestMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsResponseMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkConfiguration;

/**
 * @author lambdaprime intid@protonmail.com
 */
@ExtendWith({ElasticsearchMetricsExtension.class})
public class JRos2ServiceIntegrationTests {

    private static final String TOPIC_NAME = "jros_add_two_ints";
    private JRos2Client jrosClient;

    @BeforeAll
    public static void setupAll() {
        XLogger.load("jros2services-test.properties");
    }

    @BeforeEach
    public void setup() throws MalformedURLException {
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
    public void clean() throws Exception {
        jrosClient.close();
    }

    @Test
    public void test_happy() throws Exception {
        var seeds = new Random().ints(17, 0, 1_000).distinct().mapToObj(Integer::valueOf).toList();
        var clientLogs = Files.createTempDirectory("test_sendRequest");
        var clientProcs =
                seeds.stream()
                        .map(
                                seed ->
                                        new XExec(
                                                        "ws2/out.%s/build/examples_rclcpp_minimal_client/client_main %s %d --ros-args --log-level DEBUG"
                                                                .formatted(
                                                                        System.getenv("ROS_DISTRO"),
                                                                        TOPIC_NAME,
                                                                        seed))
                                                .start()
                                                .stderrAsync(
                                                        line -> {
                                                            try {
                                                                Files.writeString(
                                                                        clientLogs.resolve(
                                                                                "" + seed),
                                                                        line + "\n",
                                                                        StandardOpenOption.CREATE,
                                                                        StandardOpenOption.APPEND);
                                                                System.out.println(line);
                                                            } catch (IOException e) {
                                                                e.printStackTrace();
                                                            }
                                                        }))
                        .toList();
        try (var service =
                new JRos2ServicesFactory()
                        .createService(
                                jrosClient,
                                new AddTwoIntsServiceDefinition(),
                                TOPIC_NAME,
                                this::proc)) {
            service.start();
            for (int i = 0; i < seeds.size(); i++) {
                var proc = clientProcs.get(i);
                Assertions.assertEquals(0, proc.await());
                var actualOutput =
                        Files.readAllLines(clientLogs.resolve("" + seeds.get(i))).stream()
                                .filter(l -> l.startsWith("[INFO]"))
                                .filter(l -> !l.contains("waiting for service to appear"))
                                .map(l -> l.replaceAll("\\[.*\\]", ""))
                                .collect(Collectors.joining("\n"));
                Assertions.assertEquals(generateExpectedOutput(seeds.get(i)), actualOutput);
            }
        } finally {
            clientProcs.forEach(XProcess::destroyAllForcibly);
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
}
