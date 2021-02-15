/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.gateway.handlers.streaming.throttling;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.gateway.throttling.publisher.ThrottleDataPublisher;
import org.wso2.carbon.databridge.agent.DataPublisher;

public class StreamingApiThrottleDataPublisher {

    private static final Log log = LogFactory.getLog(StreamingApiThrottleDataPublisher.class);
    private static final String THROTTLE_STREAM_ID = "org.wso2.throttle.request.stream:1.0.0";

    public void publishNonThrottledEvent(int eventCount, String applicationLevelThrottleKey,
                                         String applicationLevelTier, String apiLevelThrottleKey, String apiLevelTier,
                                         String subscriptionLevelThrottleKey, String subscriptionLevelTier,
                                         String resourceLevelThrottleKey, String resourceLevelTier,
                                         String authorizedUser, String messageId, String apiName, String apiContext,
                                         String apiVersion, String appTenant, String apiTenant, String appId,
                                         JSONObject properties) {

        Object[] objects =
                new Object[] { messageId, applicationLevelThrottleKey, applicationLevelTier, apiLevelThrottleKey,
                        apiLevelTier, subscriptionLevelThrottleKey, subscriptionLevelTier, resourceLevelThrottleKey,
                        resourceLevelTier, authorizedUser, apiContext, apiVersion, appTenant, apiTenant, appId, apiName,
                        properties.toString() };

        org.wso2.carbon.databridge.commons.Event event = new org.wso2.carbon.databridge.commons.Event(
                THROTTLE_STREAM_ID, System.currentTimeMillis(), null, null, objects);

        ThrottleDataPublisher throttleDataPublisher = ServiceReferenceHolder.getInstance().getThrottleDataPublisher();
        if (throttleDataPublisher != null) {
            int count = 1;
            DataPublisher publisher = throttleDataPublisher.getDataPublisher();
            while (count <= eventCount) {
                publisher.tryPublish(event);
                count++;
            }
        } else {
            log.error("Cannot publish events to traffic manager because ThrottleDataPublisher "
                              + "has not been initialised");
        }

    }

}
