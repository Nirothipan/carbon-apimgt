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

import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.transport.passthru.DefaultStreamInterceptor;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.gateway.handlers.streaming.throttling.StreamingApiThrottleDataPublisher;
import org.wso2.carbon.apimgt.gateway.handlers.throttling.APIThrottleConstants;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.wso2.carbon.apimgt.gateway.handlers.streaming.sse.SseApiConstants.SSE_THROTTLE_DTO;

/**
 * This is used for handling throttling, and analytics event publishing of sse apis (subset of streaming apis).
 */
public class SseStreamInterceptor extends DefaultStreamInterceptor {

    private static final Log log = LogFactory.getLog(SseStreamInterceptor.class);
    private static final String SSE_STREAM_DELIMITER = "\n\n";
    private static final int DEFAULT_NO_OF_THROTTLE_PUBLISHER_EXECUTORS = 100;
    private String charset = StandardCharsets.UTF_8.name();
    private ExecutorService throttlePublisherService;
    private int noOfExecutorThreads = DEFAULT_NO_OF_THROTTLE_PUBLISHER_EXECUTORS;

    public SseStreamInterceptor() {
        throttlePublisherService = Executors.newFixedThreadPool(noOfExecutorThreads);
    }

    /**
     * Check if the request is throttled
     *
     * @param resourceLevelThrottleKey     resource level key
     * @param subscriptionLevelThrottleKey subscription level key
     * @param applicationLevelThrottleKey  application level key
     * @return is throttled or not
     */
    private static boolean isThrottled(String resourceLevelThrottleKey, String subscriptionLevelThrottleKey,
                                       String applicationLevelThrottleKey) {
        boolean isApiLevelThrottled = ServiceReferenceHolder.getInstance().getThrottleDataHolder().isAPIThrottled(
                resourceLevelThrottleKey);
        boolean isSubscriptionLevelThrottled = ServiceReferenceHolder.getInstance().getThrottleDataHolder().isThrottled(
                subscriptionLevelThrottleKey);
        boolean isApplicationLevelThrottled = ServiceReferenceHolder.getInstance().getThrottleDataHolder().isThrottled(
                applicationLevelThrottleKey);
        return (isApiLevelThrottled || isApplicationLevelThrottled || isSubscriptionLevelThrottled);
    }

    @Override
    public boolean interceptTargetResponse(MessageContext axisCtx) {
        Object artifactType = axisCtx.getProperty(PassThroughConstants.SYNAPSE_ARTIFACT_TYPE);
        return APIConstants.API_TYPE_SSE.equals(artifactType);
    }

    @Override
    public boolean targetResponse(ByteBuffer buffer, MessageContext axis2Ctx) {
        int eventCount = getEventCount(buffer);
        log.info("No. of events: " + eventCount); // todo -remove
        if (log.isDebugEnabled()) {
            log.debug("No. of events =" + eventCount);
        }
        if (eventCount > 0) {
            return handleThrottlingAndAnalytics(eventCount, axis2Ctx);
        }
        return true;
    }

    public void setNoOfExecutorThreads(int executorThreads) {
        this.noOfExecutorThreads = executorThreads;
    }

    private int getEventCount(ByteBuffer stream) {
        Charset charsetValue = Charset.forName(this.charset);
        String text = charsetValue.decode(stream).toString();
        return StringUtils.countMatches(text, SSE_STREAM_DELIMITER);
    }

    private boolean handleThrottlingAndAnalytics(int eventCount, MessageContext axi2Ctx) {

        Object throttleObject = axi2Ctx.getProperty(SSE_THROTTLE_DTO);
        if (throttleObject != null) {
            ThrottleDTO throttleDTO = (ThrottleDTO) throttleObject;
            String applicationLevelTier = throttleDTO.getApplicationTier();
            String apiLevelTier = throttleDTO.getApiTier();
            String subscriptionLevelTier = throttleDTO.getTier();
            String resourceLevelTier = throttleDTO.getResourceTier();
            String apiName = throttleDTO.getApiName();
            String authorizedUser;
            if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(
                    throttleDTO.getSubscriberTenantDomain())) {
                authorizedUser = throttleDTO.getSubscriber() + "@" + throttleDTO.getSubscriberTenantDomain();
            } else {
                authorizedUser = throttleDTO.getSubscriber();
            }
            String apiContext = throttleDTO.getApiContext();
            String apiVersion = throttleDTO.getApiVersion();
            String appTenant = throttleDTO.getSubscriberTenantDomain();
            String apiTenant = throttleDTO.getSubscriberTenantDomain();
            String appId = throttleDTO.getApplicationId();
            String applicationLevelThrottleKey = appId + ":" + authorizedUser;
            String apiLevelThrottleKey = throttleDTO.getApiContext() + ":" + apiVersion;
            String resourceLevelThrottleKey = throttleDTO.getResourceLevelThrottleKey();
            String subscriptionLevelThrottleKey = appId + ":" + throttleDTO.getApiContext() + ":" + apiVersion;
            String messageId = UIDGenerator.generateURNString();
            String remoteIP = throttleDTO.getRemoteIp();
            String tenantDomain = throttleDTO.getSubscriberTenantDomain();

            JSONObject jsonObMap = new JSONObject();
            if (remoteIP != null && remoteIP.length() > 0) {
                try {
                    InetAddress address = APIUtil.getAddress(remoteIP);
                    if (address instanceof Inet4Address) {
                        jsonObMap.put(APIThrottleConstants.IP, APIUtil.ipToLong(remoteIP));
                    } else if (address instanceof Inet6Address) {
                        jsonObMap.put(APIThrottleConstants.IPv6, APIUtil.ipToBigInteger(remoteIP));
                    }
                } catch (UnknownHostException ex) {
                    log.error("Error while parsing host IP " + remoteIP, ex);
                }
            }
            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                boolean isThrottled = isThrottled(resourceLevelThrottleKey, subscriptionLevelThrottleKey,
                                                  applicationLevelThrottleKey);
                if (isThrottled) {
                    return false;
                }
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
            StreamingApiThrottleDataPublisher dataPublisher = new StreamingApiThrottleDataPublisher();
            throttlePublisherService.execute(() -> dataPublisher
                    .publishNonThrottledEvent(eventCount, applicationLevelThrottleKey, applicationLevelTier,
                                              apiLevelThrottleKey, apiLevelTier, subscriptionLevelThrottleKey,
                                              subscriptionLevelTier, resourceLevelThrottleKey, resourceLevelTier,
                                              authorizedUser, messageId, apiName, apiContext, apiVersion, appTenant,
                                              apiTenant, appId, jsonObMap));
            return true;
        } else {
            log.error("Throttle object cannot be null.");
        }
        return true;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}
