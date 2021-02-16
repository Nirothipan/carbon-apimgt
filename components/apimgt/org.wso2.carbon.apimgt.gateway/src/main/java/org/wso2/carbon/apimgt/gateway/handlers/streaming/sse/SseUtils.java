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

package org.wso2.carbon.apimgt.gateway.handlers.streaming.sse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.context.PrivilegedCarbonContext;

public class SseUtils {

    private static final Log log = LogFactory.getLog(SseUtils.class);

    /**
     * Check if the request is throttled
     *
     * @param resourceLevelThrottleKey     resource level key
     * @param subscriptionLevelThrottleKey subscription level key
     * @param applicationLevelThrottleKey  application level key
     * @return is throttled or not
     */
    public static boolean isThrottled(String tenantDomain, String resourceLevelThrottleKey,
                                      String subscriptionLevelThrottleKey, String applicationLevelThrottleKey) {

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            boolean isApiLevelThrottled = ServiceReferenceHolder.getInstance().getThrottleDataHolder().isAPIThrottled(
                    resourceLevelThrottleKey);
            boolean isSubscriptionLevelThrottled =
                    ServiceReferenceHolder.getInstance().getThrottleDataHolder().isThrottled(
                            subscriptionLevelThrottleKey);
            boolean isApplicationLevelThrottled =
                    ServiceReferenceHolder.getInstance().getThrottleDataHolder().isThrottled(
                            applicationLevelThrottleKey);
            log.info("Throttle result \n" + "isApiLevelThrottled : " + isApiLevelThrottled
                             + "\nisSubscriptionLevelThrottled : " + isSubscriptionLevelThrottled
                             + "\nisApplicationLevelThrottled : " + isApplicationLevelThrottled);
            if (log.isDebugEnabled()) {
                log.debug("Throttle result \n" + "isApiLevelThrottled : " + isApiLevelThrottled
                                  + "\nisSubscriptionLevelThrottled : " + isSubscriptionLevelThrottled
                                  + "\nisApplicationLevelThrottled : " + isApplicationLevelThrottled);
            }
            return (isApiLevelThrottled || isApplicationLevelThrottled || isSubscriptionLevelThrottled);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }
}
