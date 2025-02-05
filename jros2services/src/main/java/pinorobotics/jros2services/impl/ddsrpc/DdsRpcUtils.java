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
package pinorobotics.jros2services.impl.ddsrpc;

import id.jros2client.impl.rmw.DdsQosMapper;
import id.jros2client.qos.SubscriberQos;
import id.xfunction.Preconditions;
import id.xfunction.logging.XLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import pinorobotics.rtpstalk.messages.RtpsTalkDataMessage;
import pinorobotics.rtpstalk.messages.UserParameterId;
import pinorobotics.rtpstalk.qos.SubscriberQosPolicy;

/**
 * @author lambdaprime intid@protonmail.com
 */
public class DdsRpcUtils {
    private static final XLogger LOGGER = XLogger.getLogger(DdsRpcUtils.class);
    private static final String MISMATCH_ERROR =
            "Mismatch between FastDDS legacy identity and DDS-RPC related identity";

    public static final SubscriberQosPolicy DEFAULT_SUBSCRIBER_QOS =
            new DdsQosMapper().asDds(SubscriberQos.DEFAULT_SUBSCRIBER_QOS);

    public Optional<Long> findRequestId(RtpsTalkDataMessage message) {
        var fastDdsIdentity = findRequestId(message, UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY);
        var relatedIdentity = findRequestId(message, UserParameterId.PID_RELATED_SAMPLE_IDENTITY);
        if (fastDdsIdentity.isEmpty() && relatedIdentity.isEmpty()) {
            LOGGER.warning("No request id found: RTPS message without identity");
            return Optional.empty();
        }
        if (fastDdsIdentity.isPresent() && relatedIdentity.isPresent()) {
            Preconditions.equals(fastDdsIdentity, relatedIdentity, MISMATCH_ERROR);
            return relatedIdentity;
        }
        return fastDdsIdentity.or(() -> relatedIdentity);
    }

    private Optional<Long> findRequestId(RtpsTalkDataMessage message, short parameterId) {
        var userInlineQos = message.userInlineQos().orElse(null);
        if (userInlineQos == null) {
            LOGGER.warning("No request id found: RTPS message without inlineQos");
            return Optional.empty();
        }
        var params = userInlineQos.getParameters();
        if (!params.containsKey(parameterId)) {
            return Optional.empty();
        }
        var identityBody = params.get(parameterId);
        var identity = SampleIdentity.valueOf(identityBody);
        return Optional.of(identity.seqNum());
    }

    /** Identity with list of parameters assigned to it */
    public record IdentityResult(byte[] identity, List<Short> parameterIds) {}

    public Optional<IdentityResult> findIdentity(RtpsTalkDataMessage message) {
        var userInlineQos = message.userInlineQos().orElse(null);
        if (userInlineQos == null) {
            LOGGER.warning("No identity found: RTPS message without inlineQos");
            return Optional.empty();
        }
        var params = userInlineQos.getParameters();
        var parameterIds = new ArrayList<Short>();
        var fastDdsIdentity = params.get(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY);
        if (fastDdsIdentity != null) parameterIds.add(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY);
        var relatedIdentity = params.get(UserParameterId.PID_RELATED_SAMPLE_IDENTITY);
        if (relatedIdentity != null) parameterIds.add(UserParameterId.PID_RELATED_SAMPLE_IDENTITY);
        if (fastDdsIdentity == null && relatedIdentity == null) {
            LOGGER.warning("No request id found: RTPS message without identity");
            return Optional.empty();
        }
        if (fastDdsIdentity != null && relatedIdentity != null)
            Preconditions.isTrue(Arrays.equals(fastDdsIdentity, relatedIdentity), MISMATCH_ERROR);
        return Optional.ofNullable(fastDdsIdentity)
                .or(() -> Optional.of(relatedIdentity))
                .map(identity -> new IdentityResult(identity, parameterIds));
    }
}
