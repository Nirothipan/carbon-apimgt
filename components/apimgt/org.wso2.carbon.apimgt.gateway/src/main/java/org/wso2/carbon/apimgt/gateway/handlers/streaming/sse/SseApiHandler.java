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

import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIAuthenticationHandler;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityUtils;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.gateway.utils.GatewayUtils;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.VerbInfoDTO;

import java.util.List;

import static org.apache.axis2.Constants.Configuration.HTTP_METHOD;
import static org.wso2.carbon.apimgt.gateway.handlers.streaming.sse.SseApiConstants.SSE_THROTTLE_DTO;

/**
 * Wraps the authentication handler for the purpose of changing the http method before calling it.
 */
public class SseApiHandler extends APIAuthenticationHandler {

    private static final Log log = LogFactory.getLog(SseApiHandler.class);

    @Override
    public boolean handleRequest(MessageContext synCtx) {

        org.apache.axis2.context.MessageContext axisCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        Object httpVerb = axisCtx.getProperty(HTTP_METHOD);
        axisCtx.setProperty(HTTP_METHOD, APIConstants.SubscriptionCreatedStatus.SUBSCRIBE);
        boolean isAuthenticated = super.handleRequest(synCtx);
        axisCtx.setProperty(Constants.Configuration.HTTP_METHOD, httpVerb);
        synCtx.setProperty(org.wso2.carbon.apimgt.gateway.handlers.analytics.Constants.SKIP_DEFAULT_METRICS_PUBLISHING,
                           true);
        if (isAuthenticated) {
            ThrottleInfo throttleInfo = getThrottlingInfo(synCtx);
            boolean isThrottled = SseUtils.isThrottled(throttleInfo.getSubscriberTenantDomain(),
                                                       throttleInfo.getResourceLevelThrottleKey(),
                                                       throttleInfo.getSubscriptionLevelThrottleKey(),
                                                       throttleInfo.getApplicationLevelThrottleKey());
            if (isThrottled) {
                log.warn("Request is throttled out");
                return false;
            }
            axisCtx.setProperty(PassThroughConstants.SYNAPSE_ARTIFACT_TYPE, APIConstants.API_TYPE_SSE);
        }
        publishSubscriptionEvent(synCtx);
        return isAuthenticated;
    }

    private ThrottleInfo getThrottlingInfo(MessageContext synCtx) {

        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) synCtx).
                getAxis2MessageContext();
        AuthenticationContext authenticationContext = APISecurityUtils.getAuthenticationContext(synCtx);
        String apiContext = (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT);
        String apiVersion = (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API_VERSION);
        List<VerbInfoDTO> verbInfoList = (List<VerbInfoDTO>) synCtx.getProperty(APIConstants.VERB_INFO_DTO);
        String resourceLevelThrottleKey = null;
        String resourceLevelTier = null;
        if (verbInfoList != null) {
            // for sse, there will be only one verb info list
            VerbInfoDTO verbInfoDTO = verbInfoList.get(0);
            resourceLevelThrottleKey = verbInfoDTO.getRequestKey();
            resourceLevelTier = verbInfoDTO.getThrottling();
        }
        String remoteIP = GatewayUtils.getIp(axis2MC);
        ThrottleInfo throttleInfo = new ThrottleInfo(authenticationContext, apiContext, apiVersion,
                                                     resourceLevelThrottleKey, resourceLevelTier, remoteIP);
        axis2MC.setProperty(SSE_THROTTLE_DTO, throttleInfo);
        return throttleInfo;
    }

    private void publishSubscriptionEvent(MessageContext synCtx) {
        //   todo
    }

}

