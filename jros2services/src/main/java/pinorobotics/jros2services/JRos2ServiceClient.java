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
package pinorobotics.jros2services;

import id.jros2client.impl.JRos2ClientConstants;
import id.jros2client.impl.rmw.DdsNameMapper;
import id.jros2client.impl.rmw.RmwConstants;
import id.jros2messages.Ros2MessageSerializationUtils;
import id.jroscommon.RosName;
import id.jrosmessages.Message;
import id.xfunction.concurrent.flow.SimpleSubscriber;
import id.xfunction.logging.XLogger;
import id.xfunction.util.IdempotentService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicLong;
import pinorobotics.jros2services.impl.ddsrpc.DdsRpcUtils;
import pinorobotics.jros2services.impl.ddsrpc.SampleIdentity;
import pinorobotics.jrosservices.JRosServiceClient;
import pinorobotics.jrosservices.metrics.JRosServiceClientMetrics;
import pinorobotics.jrosservices.msgs.ServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkClient;
import pinorobotics.rtpstalk.messages.Parameters;
import pinorobotics.rtpstalk.messages.RtpsTalkDataMessage;
import pinorobotics.rtpstalk.messages.UserParameterId;

/**
 * Client which allows to interact with ROS2 Services.
 *
 * @see <a
 *     href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 *     Services</a>
 * @param <R> request message type
 * @param <A> response message type
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2ServiceClient<R extends Message, A extends Message> extends IdempotentService
        implements JRosServiceClient<R, A> {

    private static final XLogger LOGGER = XLogger.getLogger(JRos2ServiceClient.class);

    private static final Meter METER =
            GlobalOpenTelemetry.getMeter(JRos2ServiceClient.class.getSimpleName());
    private static final LongCounter REQUESTS_METER =
            METER.counterBuilder(JRosServiceClientMetrics.REQUESTS_SENT_COUNT_METRIC)
                    .setDescription(JRosServiceClientMetrics.REQUESTS_SENT_COUNT_METRIC_DESCRIPTION)
                    .build();
    private static final LongCounter RESPONSES_METER =
            METER.counterBuilder(JRosServiceClientMetrics.RESPONSES_RECEIVED_COUNT_METRIC)
                    .setDescription(
                            JRosServiceClientMetrics.RESPONSES_RECEIVED_COUNT_METRIC_DESCRIPTION)
                    .build();
    private static final LongHistogram GOAL_EXECUTION_TIME_METER =
            METER.histogramBuilder(JRosServiceClientMetrics.CLIENT_GOAL_EXECUTION_TIME_METRIC)
                    .setDescription(
                            JRosServiceClientMetrics.CLIENT_GOAL_EXECUTION_TIME_METRIC_DESCRIPTION)
                    .ofLongs()
                    .build();

    private record PendingRequest<T>(CompletableFuture<T> future, Instant requestedAt) {
        public PendingRequest(CompletableFuture<T> result) {
            this(result, Instant.now());
        }
    }

    private final Ros2MessageSerializationUtils serializationUtils =
            new Ros2MessageSerializationUtils();
    private final Map<Long, PendingRequest<A>> pendingRequests = new ConcurrentHashMap<>();
    private final DdsRpcUtils utils = new DdsRpcUtils();
    private final AtomicLong requestCounter = new AtomicLong();
    private final DdsNameMapper rosNameMapper;
    private final RtpsTalkClient rtpsTalkClient;
    private final ServiceDefinition<R, A> serviceDefinition;
    private final RosName serviceName;
    private final Attributes metricAttributes;
    private SubmissionPublisher<RtpsTalkDataMessage> requestsPublisher;
    private SimpleSubscriber<RtpsTalkDataMessage> responsesSubscriber;
    private byte[] clientGuid;

    /** Creates a new instance of the client */
    JRos2ServiceClient(
            RtpsTalkClient rtpsTalkClient,
            ServiceDefinition<R, A> serviceDefinition,
            RosName serviceName,
            DdsNameMapper rosNameMapper) {
        this.rtpsTalkClient = rtpsTalkClient;
        this.serviceDefinition = serviceDefinition;
        this.serviceName = serviceName;
        this.rosNameMapper = rosNameMapper;
        metricAttributes =
                Attributes.builder()
                        .putAll(JRos2ClientConstants.METRIC_ATTRS)
                        .put("service", serviceName.toGlobalName())
                        .build();
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<A> sendRequestAsync(R requestMessage) {
        LOGGER.entering("sendRequest " + serviceName);
        start();
        var requestId = requestCounter.incrementAndGet();
        var data = serializationUtils.write(requestMessage);
        LOGGER.fine("Submitting request for {0}", serviceName);
        REQUESTS_METER.add(1, metricAttributes);
        requestsPublisher.submit(newMessage(requestId, data));

        // register a new subscriber
        var future = new CompletableFuture<A>();
        pendingRequests.put(requestId, new PendingRequest<A>(future));

        LOGGER.exiting("sendRequest " + serviceName);
        return future;
    }

    /**
     * @hidden exclude from javadoc
     */
    @Override
    protected void onClose() {
        LOGGER.entering("close " + serviceName);
        requestsPublisher.close();
        responsesSubscriber.getSubscription().ifPresent(Subscription::cancel);
        pendingRequests
                .values()
                .forEach(
                        result ->
                                result.future.completeExceptionally(
                                        new RuntimeException("JRos2ServiceClient has closed")));
        LOGGER.exiting("close " + serviceName);
    }

    /**
     * @hidden exclude from javadoc
     */
    @Override
    protected void onStart() {
        LOGGER.fine("Starting service client for {0}", serviceName);
        setupResponseSubscriber();
        setupRequestPublisher();
    }

    private void setupResponseSubscriber() {
        var messageDescriptor = serviceDefinition.getServiceResponseMessage();
        var rmwMessageType = rosNameMapper.asFullyQualifiedDdsTypeName(messageDescriptor);
        var rmwTopicName =
                rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, messageDescriptor);
        responsesSubscriber =
                new SimpleSubscriber<>() {
                    @Override
                    public void onNext(RtpsTalkDataMessage message) {
                        LOGGER.entering("onNext " + serviceName);
                        RESPONSES_METER.add(1, metricAttributes);
                        try {
                            var requestId = utils.findRequestId(message).orElse(null);
                            if (requestId == null) {
                                LOGGER.warning("Received response without request id, ignoring it");
                                return;
                            }
                            if (!pendingRequests.containsKey(requestId)) {
                                LOGGER.warning(
                                        "Cannot match received response with any known"
                                                + " requests. Ignoring response {0}...",
                                        requestId);
                                return;
                            }
                            LOGGER.fine("Received result for goal id {0}", requestId);
                            var result = pendingRequests.get(requestId);
                            GOAL_EXECUTION_TIME_METER.record(
                                    Duration.between(result.requestedAt, Instant.now()).toMillis(),
                                    metricAttributes);
                            var data = message.data().orElse(null);
                            if (data == null) {
                                LOGGER.warning("RTPS message has no data in it, ignoring it");
                                return;
                            }
                            var response =
                                    serializationUtils.read(
                                            data,
                                            serviceDefinition
                                                    .getServiceResponseMessage()
                                                    .getMessageClass());
                            result.future.complete(response);
                        } finally {
                            // request next message
                            getSubscription().get().request(1);
                            LOGGER.exiting("onNext " + serviceName);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        super.onError(throwable);
                        pendingRequests
                                .values()
                                .forEach(res -> res.future.completeExceptionally(throwable));
                    }
                };
        LOGGER.fine("Registering subscriber for {0} with type {1}", rmwTopicName, rmwMessageType);
        var entityId =
                rtpsTalkClient.subscribe(
                        rmwTopicName,
                        rmwMessageType,
                        DdsRpcUtils.DEFAULT_SUBSCRIBER_QOS,
                        responsesSubscriber);
        clientGuid = newClientGuid(rtpsTalkClient.getConfiguration().guidPrefix(), entityId);
    }

    private void setupRequestPublisher() {
        var messageDescriptor = serviceDefinition.getServiceRequestMessage();
        var rmwMessageType = rosNameMapper.asFullyQualifiedDdsTypeName(messageDescriptor);
        var rmwTopicName =
                rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, messageDescriptor);
        requestsPublisher = new SubmissionPublisher<RtpsTalkDataMessage>();
        LOGGER.fine("Registering publisher for {0} with type {1}", rmwTopicName, rmwMessageType);
        rtpsTalkClient.publish(
                rmwTopicName,
                rmwMessageType,
                RmwConstants.DEFAULT_PUBLISHER_QOS,
                requestsPublisher);
    }

    private RtpsTalkDataMessage newMessage(long requestId, byte[] data) {
        var params =
                new Parameters(
                        Map.of(
                                UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY,
                                new SampleIdentity(clientGuid, requestId).toByteArray()));
        return new RtpsTalkDataMessage(params, data);
    }

    private byte[] newClientGuid(byte[] guidPrefix, int entityId) {
        var clientGuid = ByteBuffer.allocate(16);
        clientGuid.put(guidPrefix);
        clientGuid.putInt(entityId);
        return clientGuid.array();
    }
}
