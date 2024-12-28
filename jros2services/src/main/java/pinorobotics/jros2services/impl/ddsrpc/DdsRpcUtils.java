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
import id.xfunction.logging.XLogger;
import java.util.Optional;
import pinorobotics.jros2services.JRos2ServiceClient;
import pinorobotics.rtpstalk.messages.RtpsTalkDataMessage;
import pinorobotics.rtpstalk.messages.UserParameterId;
import pinorobotics.rtpstalk.qos.SubscriberQosPolicy;

/**
 * @author lambdaprime intid@protonmail.com
 */
public class DdsRpcUtils {
    private static final XLogger LOGGER = XLogger.getLogger(JRos2ServiceClient.class);

    public static final SubscriberQosPolicy DEFAULT_SUBSCRIBER_QOS =
            new DdsQosMapper().asDds(SubscriberQos.DEFAULT_SUBSCRIBER_QOS);

    public Optional<Long> findRequestId(RtpsTalkDataMessage message) {
        var userInlineQos = message.userInlineQos().orElse(null);
        if (userInlineQos == null) {
            LOGGER.warning("No request id found: RTPS message without inlineQos");
            return Optional.empty();
        }
        var params = userInlineQos.getParameters();
        if (!params.containsKey(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY)) {
            LOGGER.warning("No request id found: RTPS message without identity");
            return Optional.empty();
        }
        var identityBody = params.get(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY);
        var identity = SampleIdentity.valueOf(identityBody);
        return Optional.of(identity.seqNum());
    }

    public Optional<byte[]> findIdentity(RtpsTalkDataMessage message) {
        var userInlineQos = message.userInlineQos().orElse(null);
        if (userInlineQos == null) {
            LOGGER.warning("No request id found: RTPS message without inlineQos");
            return Optional.empty();
        }
        var params = userInlineQos.getParameters();
        if (!params.containsKey(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY)) {
            LOGGER.warning("No request id found: RTPS message without identity");
            return Optional.empty();
        }
        return Optional.of(params.get(UserParameterId.PID_FASTDDS_SAMPLE_IDENTITY));
    }
}
