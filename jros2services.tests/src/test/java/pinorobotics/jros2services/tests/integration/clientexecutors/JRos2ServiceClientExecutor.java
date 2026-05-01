/*
 * Copyright 2026 jrosservices project
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
package pinorobotics.jros2services.tests.integration.clientexecutors;

import static java.util.stream.Collectors.joining;

import id.jros2client.JRos2Client;
import id.jros2client.JRos2ClientConfiguration;
import id.jros2client.JRos2ClientFactory;
import id.xfunction.function.Unchecked;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import pinorobotics.jros2services.JRos2ServiceClient;
import pinorobotics.jros2services.JRos2ServicesFactory;
import pinorobotics.jros2services.tests.integration.ClientExecutor;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsRequestMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsResponseMessage;
import pinorobotics.jros2services.tests.integration.example_interfaces_msgs.AddTwoIntsServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkConfiguration;

/**
 * Executor that launches the {@link JRos2ServiceClient} for a given ROS‑2 topic.
 *
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServiceClientExecutor implements ClientExecutor {

    private String serviceName;
    private int streamSize;
    private List<JRos2Client> jrosClients = new ArrayList<>();
    private List<JRos2ServiceClient<?, ?>> serviceClients = new ArrayList<>();

    public JRos2ServiceClientExecutor(int streamSize, String serviceName) {
        this.streamSize = streamSize;
        this.serviceName = serviceName;
    }

    @Override
    public List<Future<Result>> executeClientsAsync() throws Exception {

        /* ---- 1. generate the same seed list that the original test used ---- */
        List<Integer> seeds =
                new Random()
                        .ints(streamSize, 0, 1_000)
                        .distinct()
                        .mapToObj(Integer::valueOf)
                        .collect(Collectors.toList());
        List<Future<Result>> results = new ArrayList<>();

        /* ---- 3. launch a client process for each seed ---------------------- */
        for (int seed : seeds) {
            JRos2Client jrosClient =
                    new JRos2ClientFactory()
                            .createClient(
                                    new JRos2ClientConfiguration.Builder()
                                            .rtpsTalkConfiguration(
                                                    new RtpsTalkConfiguration.Builder()
                                                            .historyCacheMaxSize(1_000)
                                                            .publisherMaxBufferSize(1_000)
                                                            .build())
                                            .build());
            jrosClients.add(jrosClient);
            JRos2ServiceClient<AddTwoIntsRequestMessage, AddTwoIntsResponseMessage> serviceClient =
                    new JRos2ServicesFactory()
                            .createClient(
                                    jrosClient, new AddTwoIntsServiceDefinition(), serviceName);
            serviceClients.add(serviceClient);
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                var req = new AddTwoIntsRequestMessage(seed, i);
                var future = serviceClient.sendRequestAsync(req);
                futures.add(
                        future.thenApply(
                                response ->
                                        ": result of %d + %d = %d"
                                                .formatted(req.a, req.b, response.sum)));
            }
            CompletableFuture<String> combinedFuture =
                    CompletableFuture.allOf(futures.toArray(sz -> new CompletableFuture[sz]))
                            .thenApply(
                                    empty ->
                                            futures.stream()
                                                    .map(f -> Unchecked.get(() -> f.get()))
                                                    .collect(joining("\n")));
            results.add(combinedFuture.thenApply(output -> new Result(seed, output)));
        }
        return results;
    }

    @Override
    public void close() {
        serviceClients.forEach(JRos2ServiceClient::close);
        jrosClients.forEach(JRos2Client::close);
    }
}
