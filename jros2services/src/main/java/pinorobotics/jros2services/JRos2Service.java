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

import id.jros2client.impl.JRos2ClientConstants;
import id.jros2client.impl.rmw.DdsNameMapper;
import id.jros2client.impl.rmw.RmwConstants;
import id.jros2messages.Ros2MessageSerializationUtils;
import id.jrosmessages.Message;
import id.xfunction.concurrent.flow.SimpleSubscriber;
import id.xfunction.logging.XLogger;
import id.xfunction.util.IdempotentService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;
import java.util.stream.Collectors;
import pinorobotics.jros2services.impl.ddsrpc.DdsRpcUtils;
import pinorobotics.jrosservices.metrics.JRosServiceMetrics;
import pinorobotics.jrosservices.msgs.ServiceDefinition;
import pinorobotics.rtpstalk.RtpsTalkClient;
import pinorobotics.rtpstalk.messages.Parameters;
import pinorobotics.rtpstalk.messages.RtpsTalkDataMessage;

/**
 * ROS2 Service
 *
 * @see <a
 *     href="https://docs.ros.org/en/galactic/Tutorials/Services/Understanding-ROS2-Services.html">ROS2
 *     Services</a>
 * @param <R> request message type
 * @param <A> response message type
 * @author lambdaprime intid@protonmail.com
 */
public class JRos2Service<R extends Message, A extends Message> extends IdempotentService {

    private static final XLogger LOGGER = XLogger.getLogger(JRos2Service.class);

    private static final Meter METER =
            GlobalOpenTelemetry.getMeter(JRos2Service.class.getSimpleName());
    private static final LongCounter REQUESTS_METER =
            METER.counterBuilder(JRosServiceMetrics.REQUESTS_RECEIVED_COUNT_METRIC)
                    .setDescription(JRosServiceMetrics.REQUESTS_RECEIVED_COUNT_METRIC_DESCRIPTION)
                    .build();
    private static final LongCounter REQUESTS_FAILED_METER =
            METER.counterBuilder(JRosServiceMetrics.REQUESTS_FAILED_COUNT_METRIC)
                    .setDescription(JRosServiceMetrics.REQUESTS_FAILED_COUNT_METRIC_DESCRIPTION)
                    .build();
    private static final LongHistogram GOAL_EXECUTION_TIME_METER =
            METER.histogramBuilder(JRosServiceMetrics.SERVICE_GOAL_EXECUTION_TIME_METRIC)
                    .setDescription(
                            JRosServiceMetrics.SERVICE_GOAL_EXECUTION_TIME_METRIC_DESCRIPTION)
                    .ofLongs()
                    .build();

    private Ros2MessageSerializationUtils serializationUtils = new Ros2MessageSerializationUtils();
    private DdsRpcUtils utils = new DdsRpcUtils();
    private RtpsTalkClient rtpsTalkClient;
    private ServiceDefinition<R, A> serviceDefinition;
    private ExecutorService executor;
    private String serviceName;
    private Function<R, A> handler;
    private DdsNameMapper rosNameMapper;
    private SubmissionPublisher<RtpsTalkDataMessage> responsesPublisher;
    private SimpleSubscriber<RtpsTalkDataMessage> requestsSubscriber;
    private Attributes metricAttributes;

    /**
     * @param handler service function which will process all incoming requests
     */
    JRos2Service(
            RtpsTalkClient rtpsTalkClient,
            ServiceDefinition<R, A> serviceDefinition,
            String serviceName,
            DdsNameMapper rosNameMapper,
            ExecutorService executor,
            Function<R, A> handler) {
        this.rtpsTalkClient = rtpsTalkClient;
        this.serviceDefinition = serviceDefinition;
        this.serviceName = serviceName;
        this.rosNameMapper = rosNameMapper;
        this.executor = executor;
        this.handler = handler;
        metricAttributes =
                Attributes.builder()
                        .putAll(JRos2ClientConstants.METRIC_ATTRS)
                        .put("service", serviceName)
                        .build();
    }

    @Override
    protected void onStart() {
        LOGGER.fine("Starting service for {0}", serviceName);
        setupResponsePublisher();
        // subscribe to requests at the end when we ready to process them
        setupRequestSubscriber();
    }

    private void setupRequestSubscriber() {
        var messageClass = serviceDefinition.getServiceRequestMessage();
        var rmwMessageType = rosNameMapper.asFullyQualifiedDdsTypeName(messageClass);
        var rmwTopicName = rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, messageClass);
        requestsSubscriber =
                new SimpleSubscriber<>() {
                    @Override
                    public void onNext(RtpsTalkDataMessage message) {
                        LOGGER.entering("onNext " + serviceName);
                        REQUESTS_METER.add(1, metricAttributes);
                        try {
                            var identityResult = utils.findIdentity(message).orElse(null);
                            if (identityResult == null) {
                                LOGGER.warning("Received request without identity, ignoring it");
                                return;
                            }
                            var requestData = message.data().orElse(null);
                            if (requestData == null) {
                                LOGGER.warning("RTPS message has no data in it, ignoring it");
                                return;
                            }
                            executor.submit(
                                    () -> {
                                        try {
                                            var request =
                                                    serializationUtils.read(
                                                            requestData,
                                                            serviceDefinition
                                                                    .getServiceRequestMessage());
                                            LOGGER.fine(
                                                    "Executing new request for {0}", serviceName);
                                            var responseMessage = runHandler(request);
                                            var respomseData =
                                                    serializationUtils.write(responseMessage);
                                            REQUESTS_METER.add(1, metricAttributes);
                                            responsesPublisher.submit(
                                                    new RtpsTalkDataMessage(
                                                            new Parameters(
                                                                    identityResult
                                                                            .parameterIds()
                                                                            .stream()
                                                                            .collect(
                                                                                    Collectors
                                                                                            .toMap(
                                                                                                    k ->
                                                                                                            k,
                                                                                                    v ->
                                                                                                            identityResult
                                                                                                                    .identity()))),
                                                            respomseData));
                                        } catch (Exception e) {
                                            REQUESTS_FAILED_METER.add(1, metricAttributes);
                                            LOGGER.severe(
                                                    "Service handler exception: {0}",
                                                    e.getMessage());
                                            LOGGER.fine(e);
                                        }
                                    });
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            // request next message
                            getSubscription().get().request(1);
                            LOGGER.exiting("onNext " + serviceName);
                        }
                    }
                };

        LOGGER.fine(
                "Registering requests subscriber for {0} with type {1}",
                rmwTopicName, rmwMessageType);
        rtpsTalkClient.subscribe(
                rmwTopicName,
                rmwMessageType,
                DdsRpcUtils.DEFAULT_SUBSCRIBER_QOS,
                requestsSubscriber);
    }

    private A runHandler(R request) {
        var startAt = Instant.now();
        try {
            return handler.apply(request);
        } finally {
            GOAL_EXECUTION_TIME_METER.record(
                    Duration.between(startAt, Instant.now()).toMillis(), metricAttributes);
        }
    }

    private void setupResponsePublisher() {
        var messageClass = serviceDefinition.getServiceResponseMessage();
        var rmwMessageType = rosNameMapper.asFullyQualifiedDdsTypeName(messageClass);
        var rmwTopicName = rosNameMapper.asFullyQualifiedDdsTopicName(serviceName, messageClass);
        responsesPublisher =
                new SubmissionPublisher<RtpsTalkDataMessage>(
                        Executors.newCachedThreadPool(), 1_000);
        LOGGER.fine("Registering publisher for {0} with type {1}", rmwTopicName, rmwMessageType);
        rtpsTalkClient.publish(
                rmwTopicName,
                rmwMessageType,
                RmwConstants.DEFAULT_PUBLISHER_QOS,
                RmwConstants.DEFAULT_WRITER_SETTINGS,
                responsesPublisher);
    }

    @Override
    protected void onClose() {
        responsesPublisher.close();
        requestsSubscriber.getSubscription().ifPresent(Subscription::cancel);
    }

    /**
     * Start this ROS service
     *
     * <p>Service will be active until it is closed with {@link #close()}
     *
     * <p>{@inheritDoc}
     */
    @Override
    public void start() {
        super.start();
    }

    /**
     * Stop ROS service
     *
     * <p>{@inheritDoc}
     */
    @Override
    public void close() {
        super.close();
    }
}
