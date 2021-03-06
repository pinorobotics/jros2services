/*
 * Copyright 2021 jros2services project
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

import id.jros2client.impl.DdsNameMapper;
import id.jros2messages.MessageSerializationUtils;
import id.jrosmessages.Message;
import id.xfunction.Preconditions;
import id.xfunction.concurrent.flow.SimpleSubscriber;
import id.xfunction.logging.XLogger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.SubmissionPublisher;
import pinorobotics.ddsrpc.SampleIdentity;
import pinorobotics.jros2services.service_msgs.ServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkClient;
import pinorobotics.rtpstalk.messages.Parameters;
import pinorobotics.rtpstalk.messages.RtpsTalkDataMessage;

/**
 * Client which allows to interact with ROS2 Services.
 *
 * <p>Currently there is no document which would describe ROS2 Service communication, as the result
 * different ROS 2 Middleware Layers (RML) <a
 * href="https://github.com/ros2/rmw_cyclonedds/issues/184">implement it differently</a>.
 *
 * <p>As of now this client supports only Fast-DDS RML implementation since it was default one in
 * ROS2 Foxy.
 *
 * <p>In future it is possible that all RML implementations will switch to DDS-RPC standard (see <a
 * href="https://design.ros2.org/articles/ros_on_dds.html">Services and Actions</a>
 *
 * @see <a
 *     href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 *     Services</a>
 * @param <R> request message type
 * @param <A> response message type
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServiceClient<R extends Message, A extends Message> implements AutoCloseable {

    private static final XLogger LOGGER = XLogger.getLogger(JRos2ServiceClient.class);
    private static final short PID_FASTDDS_SAMPLE_IDENTITY = (short) 0x800f;

    private DdsNameMapper rosNameMapper;
    private RtpsTalkClient rtpsTalkClient;
    private MessageSerializationUtils serializationUtils = new MessageSerializationUtils();
    private ServiceDefinition<R, A> serviceDefinition;
    private int status; // 0 - not started, 1 - started, 2 - stopped
    private Map<Long, CompletableFuture<A>> pendingRequests = new HashMap<>();
    private SubmissionPublisher<RtpsTalkDataMessage> requestsPublisher;
    private SimpleSubscriber<RtpsTalkDataMessage> resultsSubscriber;
    private String serviceName;
    private long requestCounter = 1;
    private int entityId;

    /** Creates a new instance of the client */
    JRos2ServiceClient(
            RtpsTalkClient rtpsTalkClient,
            ServiceDefinition<R, A> serviceDefinition,
            String serviceName,
            DdsNameMapper rosNameMapper) {
        this.rtpsTalkClient = rtpsTalkClient;
        this.serviceDefinition = serviceDefinition;
        this.serviceName = serviceName;
        this.rosNameMapper = rosNameMapper;
    }

    public CompletableFuture<A> sendRequestAsync(R requestMessage) {
        LOGGER.entering("sendRequest " + serviceName);
        startLazy();
        var requestId = requestCounter++;

        var writerGuid = ByteBuffer.allocate(16);
        writerGuid.put(rtpsTalkClient.getConfiguration().guidPrefix());
        writerGuid.putInt(entityId);
        var params =
                new Parameters(
                        Map.of(
                                Short.valueOf(PID_FASTDDS_SAMPLE_IDENTITY),
                                new SampleIdentity(writerGuid.array(), requestId).toByteArray()));
        var data = serializationUtils.write(requestMessage);
        LOGGER.fine("Submitting request for {0}", serviceName);
        requestsPublisher.submit(new RtpsTalkDataMessage(params, data));

        // register a new subscriber
        var future = new CompletableFuture<A>();
        pendingRequests.put(requestId, future);

        LOGGER.exiting("sendRequest " + serviceName);
        return future;
    }

    @Override
    public void close() {
        LOGGER.entering("close " + serviceName);
        if (status == 1) {
            requestsPublisher.close();
            resultsSubscriber.getSubscription().ifPresent(Subscription::cancel);
            status++;
            pendingRequests
                    .values()
                    .forEach(
                            future ->
                                    future.completeExceptionally(
                                            new RuntimeException("JRos2ServiceClient has closed")));
        }
        LOGGER.exiting("close " + serviceName);
    }

    private void startLazy() {
        if (status == 0) {
            start();
        } else if (status != 1) {
            throw new IllegalStateException("Already stopped");
        }
    }

    private void start() {
        Preconditions.isTrue(status == 0, "Can be started only once");
        status++;
        var requestMessageClass = serviceDefinition.getServiceRequestMessage();
        var requestMessageName = rosNameMapper.asFullyQualifiedDdsTypeName(requestMessageClass);
        LOGGER.fine("Starting service client for {0}", serviceName);
        var requestTopicName =
                rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, requestMessageClass);
        requestsPublisher = new SubmissionPublisher<RtpsTalkDataMessage>();
        LOGGER.fine(
                "Registering publisher for {0} with type {1}",
                requestTopicName, requestMessageName);
        rtpsTalkClient.publish(requestTopicName, requestMessageName, requestsPublisher);

        var responseMessageClass = serviceDefinition.getServiceResponseMessage();
        var responseMessageName = rosNameMapper.asFullyQualifiedDdsTypeName(responseMessageClass);
        var responseTopicName =
                rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, responseMessageClass);
        resultsSubscriber =
                new SimpleSubscriber<>() {
                    @Override
                    public void onNext(RtpsTalkDataMessage message) {
                        LOGGER.entering("onNext " + serviceName);
                        var params = message.inlineQos().getParameters();
                        if (!params.containsKey(PID_FASTDDS_SAMPLE_IDENTITY)) {
                            LOGGER.warning("Received message without identity, ignoring it");
                        } else {
                            var identityBody = params.get(PID_FASTDDS_SAMPLE_IDENTITY);
                            var identity = SampleIdentity.valueOf(identityBody);
                            var seqNum = identity.seqNum();
                            if (!pendingRequests.containsKey(seqNum)) {
                                LOGGER.warning(
                                        "Cannot match received response with any known requests."
                                                + " Ignoring response {0}...",
                                        seqNum);
                            } else {
                                LOGGER.fine("Received result for goal id {0}", seqNum);
                                var future = pendingRequests.get(seqNum);
                                var response =
                                        serializationUtils.read(
                                                message.data(),
                                                serviceDefinition.getServiceResponseMessage());
                                future.complete(response);
                            }
                        }
                        // request next message
                        getSubscription().get().request(1);
                        LOGGER.exiting("onNext " + serviceName);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        super.onError(throwable);
                        pendingRequests.values().forEach(fu -> fu.completeExceptionally(throwable));
                    }
                };
        LOGGER.fine(
                "Registering subscriber for {0} with type {1}",
                responseTopicName, responseMessageName);
        entityId =
                rtpsTalkClient.subscribe(responseTopicName, responseMessageName, resultsSubscriber);
    }
}
