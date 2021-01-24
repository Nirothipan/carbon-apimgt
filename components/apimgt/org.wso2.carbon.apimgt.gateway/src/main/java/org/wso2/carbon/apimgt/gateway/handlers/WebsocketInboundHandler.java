/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.apimgt.gateway.handlers;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.synapse.MessageContext;
import org.apache.synapse.api.API;
import org.apache.synapse.api.ApiUtils;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.dispatch.RESTDispatcher;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIKeyValidator;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityException;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityUtils;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.gateway.handlers.security.jwt.JWTValidator;
import org.wso2.carbon.apimgt.gateway.handlers.throttling.APIThrottleConstants;
import org.wso2.carbon.apimgt.gateway.handlers.websocket.WebSocketApiException;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.gateway.utils.APIMgtGoogleAnalyticsUtils;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerAnalyticsConfiguration;
import org.wso2.carbon.apimgt.impl.caching.CacheProvider;
import org.wso2.carbon.apimgt.impl.dto.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.impl.jwt.SignedJWTInfo;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.usage.publisher.APIMgtUsageDataPublisher;
import org.wso2.carbon.apimgt.usage.publisher.DataPublisherUtil;
import org.wso2.carbon.apimgt.usage.publisher.dto.ExecutionTimeDTO;
import org.wso2.carbon.apimgt.usage.publisher.dto.RequestResponseStreamDTO;
import org.wso2.carbon.apimgt.usage.publisher.dto.ThrottlePublisherDTO;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.ganalytics.publisher.GoogleAnalyticsData;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.cache.Cache;

import static org.wso2.carbon.apimgt.gateway.handlers.websocket.WebSocketApiConstants.URL_SEPARATOR;
import static org.wso2.carbon.apimgt.gateway.handlers.websocket.WebSocketApiConstants.WS_ENDPOINT_NAME;
import static org.wso2.carbon.apimgt.gateway.handlers.websocket.WebSocketApiConstants.WS_SECURED_ENDPOINT_NAME;

/**
 * This is a handler which is actually embedded to the netty pipeline which does operations such as
 * authentication and throttling for the websocket handshake and subsequent websocket frames.
 */
public class WebsocketInboundHandler extends ChannelInboundHandlerAdapter {
    private static final Log log = LogFactory.getLog(WebsocketInboundHandler.class);
    private String tenantDomain;
    private static APIMgtUsageDataPublisher usageDataPublisher;
    private String fullRequestUri;
    private String version;
    private APIKeyValidationInfoDTO infoDTO = new APIKeyValidationInfoDTO();
    private io.netty.handler.codec.http.HttpHeaders headers = new DefaultHttpHeaders();
    private String token;
    private String apiContext;
    private String inboundName;
    private String apiName;
    private String keyType;
    private API api;
    private static final AttributeKey<Map<String, Object>> WSO2_PROPERTIES = AttributeKey.valueOf("WSO2_PROPERTIES");

    public WebsocketInboundHandler() {
        initializeDataPublisher();
    }

    private void initializeDataPublisher() {
        if (APIUtil.isAnalyticsEnabled() && usageDataPublisher == null) {
            String publisherClass = getApiManagerAnalyticsConfiguration().getPublisherClass();

            try {
                synchronized (this) {
                    if (usageDataPublisher == null) {
                        try {
                            log.debug("Instantiating Web Socket Data Publisher");
                            usageDataPublisher = (APIMgtUsageDataPublisher) APIUtil.getClassForName(publisherClass)
                                    .newInstance();
                            usageDataPublisher.init();
                        } catch (ClassNotFoundException e) {
                            log.error("Class not found " + publisherClass, e);
                        } catch (InstantiationException e) {
                            log.error("Error instantiating " + publisherClass, e);
                        } catch (IllegalAccessException e) {
                            log.error("Illegal access to " + publisherClass, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Cannot publish event. " + e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        //check if the request is a handshake
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest req = (FullHttpRequest) msg;

            setUris(req);
            inboundName = ctx.channel().pipeline().get("ssl") != null ? WS_SECURED_ENDPOINT_NAME : WS_ENDPOINT_NAME  ;
            setTenantDomain(fullRequestUri);
            String matchingResource = getMatchingResource(ctx, getMessageContext(tenantDomain), req);

            String useragent = req.headers().get(HttpHeaders.USER_AGENT);

            // '-' is used for empty values to avoid possible errors in DAS side.
            // Required headers are stored one by one as validateOAuthHeader()
            // removes some of the headers from the request
            useragent = useragent != null ? useragent : "-";
            headers.add(HttpHeaders.USER_AGENT, useragent);

            if (validateOAuthHeader(ctx ,req, matchingResource)) {
                ctx.channel().attr(WSO2_PROPERTIES).set(getApiProperties());
                if (StringUtils.isNotEmpty(token)) {
                    ((FullHttpRequest) msg).headers().set(APIMgtGatewayConstants.WS_JWT_TOKEN_HEADER, token);
                }
                ctx.fireChannelRead(msg);

                // publish google analytics data
                GoogleAnalyticsData.DataBuilder gaData = new GoogleAnalyticsData.DataBuilder(null, null, null, null)
                        .setDocumentPath(fullRequestUri).setDocumentHostName(DataPublisherUtil.getHostAddress()).setSessionControl(
                                "end").setCacheBuster(APIMgtGoogleAnalyticsUtils.getCacheBusterId()).setIPOverride(
                                ctx.channel().remoteAddress().toString());
                APIMgtGoogleAnalyticsUtils gaUtils = new APIMgtGoogleAnalyticsUtils();
                gaUtils.init(tenantDomain);
                gaUtils.publishGATrackingData(gaData, req.headers().get(HttpHeaders.USER_AGENT),
                                              headers.get(HttpHeaders.AUTHORIZATION));
            } else {
                String errorMessage = APISecurityConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE;
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                                            HttpResponseStatus.UNAUTHORIZED,
                                                                            Unpooled.copiedBuffer(errorMessage,
                                                                                                  CharsetUtil.UTF_8));
                httpResponse.headers().set("content-type", "text/plain; charset=UTF-8");
                httpResponse.headers().set("content-length", httpResponse.content().readableBytes());
                ctx.writeAndFlush(httpResponse);
                if (log.isDebugEnabled()) {
                    log.debug("Authentication Failure for the websocket context: " + fullRequestUri);
                }
                throw new APISecurityException(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS,
                                               APISecurityConstants.API_AUTH_INVALID_CREDENTIALS_MESSAGE);
            }
        } else if ((msg instanceof CloseWebSocketFrame) || (msg instanceof PingWebSocketFrame)) {
            //if the inbound frame is a closed frame, throttling, analytics will not be published.
            ctx.fireChannelRead(msg);
        } else if (msg instanceof WebSocketFrame) {

            boolean isAllowed = doThrottle(ctx, (WebSocketFrame) msg);

            if (isAllowed) {
                ctx.fireChannelRead(msg);
                String clientIp = getRemoteIP(ctx);
                // publish analytics events if analytics is enabled
                if (APIUtil.isAnalyticsEnabled()) {
                    publishRequestEvent(clientIp, true);
                }
            } else {
                ctx.writeAndFlush(new TextWebSocketFrame("Websocket frame throttled out"));
                if (log.isDebugEnabled()) {
                    log.debug("Inbound Websocket frame is throttled. " + ctx.channel().toString());
                }
            }
        }
    }

    private void setUris(FullHttpRequest req) throws URISyntaxException {

        fullRequestUri = req.getUri();
        URI uriTemp = new URI(fullRequestUri);
        fullRequestUri = new URI(uriTemp.getScheme(), uriTemp.getAuthority(), uriTemp.getPath(), null, uriTemp.getFragment())
                .toString();
        fullRequestUri = this.fullRequestUri.endsWith(URL_SEPARATOR) ?
                fullRequestUri.substring(0, fullRequestUri.length() - 1) :
                fullRequestUri;
        if (log.isDebugEnabled()) {
            log.debug("Websocket API fullRequestUri = " + fullRequestUri);
        }
    }

    private void setTenantDomain(String uri) {

        if (uri.contains("/t/")) {
            tenantDomain = MultitenantUtils.getTenantDomainFromUrl(uri);
        } else {
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }
    }

    /**
     * Authenticate request
     *
     * @param ctx              Channel context
     * @param req              Full Http Request
     * @param matchingResource resource template matching invocation
     * @return whether authenticated or not
     * @throws APISecurityException if authentication fails
     */
    private boolean validateOAuthHeader(ChannelHandlerContext ctx, FullHttpRequest req, String matchingResource)
            throws APISecurityException {
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            APIKeyValidationInfoDTO info;
            if (!req.headers().contains(HttpHeaders.AUTHORIZATION)) {
                QueryStringDecoder decoder = new QueryStringDecoder(fullRequestUri);
                Map<String, List<String>> requestMap = decoder.parameters();
                if (requestMap.containsKey(APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT)) {
                    req.headers().add(HttpHeaders.AUTHORIZATION, APIConstants.CONSUMER_KEY_SEGMENT + ' '
                                    + requestMap.get(APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT).get(0));
                    removeTokenFromQuery(requestMap);
                } else {
                    log.error("No Authorization Header or access_token query parameter present");
                    return false;
                }
            }
            String authorizationHeader = req.headers().get(HttpHeaders.AUTHORIZATION);
            headers.add(HttpHeaders.AUTHORIZATION, authorizationHeader);
            String[] auth = authorizationHeader.split(" ");
            if (APIConstants.CONSUMER_KEY_SEGMENT.equals(auth[0])) {
                String cacheKey;
                boolean isJwtToken = false;
                String apiKey = auth[1];
                if (WebsocketUtil.isRemoveOAuthHeadersFromOutMessage()) {
                    req.headers().remove(HttpHeaders.AUTHORIZATION);
                }

                //Initial guess of a JWT token using the presence of a DOT.

                SignedJWTInfo signedJWTInfo = null;
                if (StringUtils.isNotEmpty(apiKey) && apiKey.contains(APIConstants.DOT)) {
                    try {
                        // Check if the header part is decoded
                        if (StringUtils.countMatches(apiKey, APIConstants.DOT) != 2) {
                            log.debug("Invalid JWT token. The expected token format is <header.payload.signature>");
                            throw new APISecurityException(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS,
                                                           "Invalid JWT token");
                        }
                        signedJWTInfo = getSignedJwtInfo(apiKey);
                        String keyManager = ServiceReferenceHolder.getInstance().getJwtValidationService()
                                .getKeyManagerNameIfJwtValidatorExist(signedJWTInfo);
                        if (StringUtils.isNotEmpty(keyManager)) {
                            isJwtToken = true;
                        }
                    } catch (ParseException e) {
                        log.debug("Not a JWT token. Failed to decode the token header.", e);
                    } catch (APIManagementException e) {
                        log.error("error while check validation of JWt", e);
                        throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                                                       APISecurityConstants.API_AUTH_GENERAL_ERROR_MESSAGE);
                    }
                }
                // Find the authentication scheme based on the token type
                String apiVersion = version;
                boolean isDefaultVersion = false;

                // TODO - check this
                if ((fullRequestUri.startsWith("/" + version)
                        || fullRequestUri.startsWith("/t/" + tenantDomain + "/" + version))) {
                    apiVersion = APIConstants.DEFAULT_WEBSOCKET_VERSION;
                    isDefaultVersion = true;
                }
                if (isJwtToken) {
                    log.debug("The token was identified as a JWT token");

                    AuthenticationContext authenticationContext = new JWTValidator(new APIKeyValidator()).
                            authenticateForWebSocket(signedJWTInfo, apiContext, apiVersion, matchingResource);
                    if (authenticationContext == null || !authenticationContext.isAuthenticated()) {
                        return false;
                    }
                    // The information given by the AuthenticationContext is set to an APIKeyValidationInfoDTO object
                    // so to feed information analytics and throttle data publishing
                    info = new APIKeyValidationInfoDTO();
                    info.setAuthorized(authenticationContext.isAuthenticated());
                    info.setApplicationTier(authenticationContext.getApplicationTier());
                    info.setTier(authenticationContext.getTier());
                    info.setSubscriberTenantDomain(authenticationContext.getSubscriberTenantDomain());
                    info.setSubscriber(authenticationContext.getSubscriber());
                    info.setStopOnQuotaReach(authenticationContext.isStopOnQuotaReach());
                    info.setApiName(authenticationContext.getApiName());
                    info.setApplicationId(authenticationContext.getApplicationId());
                    info.setType(authenticationContext.getKeyType());
                    info.setApiPublisher(authenticationContext.getApiPublisher());
                    info.setApplicationName(authenticationContext.getApplicationName());
                    info.setConsumerKey(authenticationContext.getConsumerKey());
                    info.setEndUserName(authenticationContext.getUsername());
                    info.setApiTier(authenticationContext.getApiTier());

                   keyType = info.getType();
                    if (isDefaultVersion) {
                        version = authenticationContext.getApiVersion();
                    }

                    infoDTO = info;
                    return authenticationContext.isAuthenticated();
                } else {
                    log.debug("The token was identified as an OAuth token");
                    //If the key have already been validated
                    if (WebsocketUtil.isGatewayTokenCacheEnabled()) {
                        cacheKey = WebsocketUtil.getAccessTokenCacheKey(apiKey, apiContext, matchingResource);
                        info = WebsocketUtil.validateCache(apiKey, cacheKey);
                        if (info != null) {
                            keyType = info.getType();
                            infoDTO = info;
                            return info.isAuthorized();
                        }
                    }
                    String keyValidatorClientType = APISecurityUtils.getKeyValidatorClientType();
                    if (APIConstants.API_KEY_VALIDATOR_WS_CLIENT.equals(keyValidatorClientType)) {
                        info = getApiKeyDataForWSClient(apiKey, tenantDomain, apiContext, apiVersion, matchingResource);
                    } else {
                        return false;
                    }
                    if (info == null || !info.isAuthorized()) {
                        return false;
                    }
                    log.info("Api name ::: " + info.getApiName());
//                    if (info.getApiName() != null && info.getApiName().contains("*")) {
//                        String[] str = info.getApiName().split("\\*");
//                        version = str[1];
//                        uri += "/" + str[1];
//                        info.setApiName(str[0]);
//                    }
                    if (WebsocketUtil.isGatewayTokenCacheEnabled()) {
                        cacheKey = WebsocketUtil.getAccessTokenCacheKey(apiKey, apiContext, matchingResource);
                        WebsocketUtil.putCache(info, apiKey, cacheKey);
                    }
                    keyType = info.getType();
                    token = info.getEndUserToken();
                    infoDTO = info;
                    return true;
                }
            } else {
                return false;
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private Map<String, Object> getApiProperties() {

        Map<String, Object> apiPropertiesMap = new HashMap();
        apiPropertiesMap.put(RESTConstants.SYNAPSE_REST_API, apiName);
        apiPropertiesMap.put(RESTConstants.PROCESSED_API, api);
        apiPropertiesMap.put(APIConstants.API_KEY_TYPE, keyType);
        return apiPropertiesMap;
    }

    private APIKeyValidationInfoDTO getApiKeyDataForWSClient(String key, String domain, String apiContextUri,
                                                             String apiVersion, String matchingResource)
            throws APISecurityException {

        return new WebsocketWSClient().getAPIKeyData(apiContextUri, apiVersion, key, domain, matchingResource);
    }

    protected APIManagerAnalyticsConfiguration getApiManagerAnalyticsConfiguration() {
        return DataPublisherUtil.getApiManagerAnalyticsConfiguration();
    }

    /**
     * Checks if the request is throttled
     *
     * @param ctx ChannelHandlerContext
     * @return false if throttled
     * @throws APIManagementException
     */
    public boolean doThrottle(ChannelHandlerContext ctx, WebSocketFrame msg) {

        String applicationLevelTier = infoDTO.getApplicationTier();
        String apiLevelTier = infoDTO.getApiTier();
        String subscriptionLevelTier = infoDTO.getTier();
        String resourceLevelTier = apiLevelTier;
        String authorizedUser;
        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(infoDTO.getSubscriberTenantDomain())) {
            authorizedUser = infoDTO.getSubscriber() + "@" + infoDTO.getSubscriberTenantDomain();
        } else {
            authorizedUser = infoDTO.getSubscriber();
        }
        String apiName = infoDTO.getApiName();
        String apiVersion = version;
        String appTenant = infoDTO.getSubscriberTenantDomain();
        String apiTenant = tenantDomain;
        String appId = infoDTO.getApplicationId();
        String applicationLevelThrottleKey = appId + ":" + authorizedUser;
        String apiLevelThrottleKey = apiContext + ":" + apiVersion;
        String resourceLevelThrottleKey = apiLevelThrottleKey;
        String subscriptionLevelThrottleKey = appId + ":" + apiContext + ":" + apiVersion;
        String messageId = UIDGenerator.generateURNString();
        String remoteIP = getRemoteIP(ctx);
        if (log.isDebugEnabled()) {
            log.debug("Remote IP address : " + remoteIP);
        }
        if (remoteIP.indexOf(":") > 0) {
            remoteIP = remoteIP.substring(1, remoteIP.indexOf(":"));
        }
        JSONObject jsonObMap = new JSONObject();
        if (remoteIP != null && remoteIP.length() > 0) {
            try {
                InetAddress address = APIUtil.getAddress(remoteIP);
                if (address instanceof Inet4Address) {
                    jsonObMap.put(APIThrottleConstants.IP, APIUtil.ipToLong(remoteIP));
                } else if (address instanceof Inet6Address) {
                    jsonObMap.put(APIThrottleConstants.IPv6, APIUtil.ipToBigInteger(remoteIP));
                }
            } catch (UnknownHostException e) {
                //ignore the error and log it
                log.error("Error while parsing host IP " + remoteIP, e);
            }
        }
        jsonObMap.put(APIThrottleConstants.MESSAGE_SIZE, msg.content().capacity());
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            boolean isThrottled = WebsocketUtil.isThrottled(resourceLevelThrottleKey, subscriptionLevelThrottleKey,
                                                            applicationLevelThrottleKey);
            if (isThrottled) {
                if (APIUtil.isAnalyticsEnabled()) {
                    publishThrottleEvent();
                }
                return false;
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        Object[] objects =
                new Object[] { messageId, applicationLevelThrottleKey, applicationLevelTier, apiLevelThrottleKey,
                        apiLevelTier, subscriptionLevelThrottleKey, subscriptionLevelTier, resourceLevelThrottleKey,
                        resourceLevelTier, authorizedUser, apiContext, apiVersion, appTenant, apiTenant, appId, apiName,
                        jsonObMap.toString() };
        org.wso2.carbon.databridge.commons.Event event = new org.wso2.carbon.databridge.commons.Event(
                "org.wso2.throttle.request.stream:1.0.0", System.currentTimeMillis(), null, null, objects);
        if (ServiceReferenceHolder.getInstance().getThrottleDataPublisher() == null) {
            log.error("Cannot publish events to traffic manager because ThrottleDataPublisher "
                              + "has not been initialised");
            return true;
        }
        ServiceReferenceHolder.getInstance().getThrottleDataPublisher().getDataPublisher().tryPublish(event);
        return true;
    }

    protected String getRemoteIP(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Publish reuqest event to analytics server
     *
     * @param clientIp       client's IP Address
     * @param isThrottledOut request is throttled out or not
     */
    public void publishRequestEvent(String clientIp, boolean isThrottledOut) {
        long requestTime = System.currentTimeMillis();
        String useragent = headers.get(HttpHeaders.USER_AGENT);

        try {
            String appOwner = infoDTO.getSubscriber();
            String keyType = infoDTO.getType();
            String correlationID = UUID.randomUUID().toString();

            RequestResponseStreamDTO requestPublisherDTO = new RequestResponseStreamDTO();
            requestPublisherDTO.setApiName(infoDTO.getApiName());
            requestPublisherDTO.setApiCreator(infoDTO.getApiPublisher());
            requestPublisherDTO.setApiCreatorTenantDomain(MultitenantUtils.getTenantDomain(infoDTO.getApiPublisher()));
            requestPublisherDTO.setApiVersion(infoDTO.getApiName() + ':' + version);
            requestPublisherDTO.setApplicationId(infoDTO.getApplicationId());
            requestPublisherDTO.setApplicationName(infoDTO.getApplicationName());
            requestPublisherDTO.setApplicationOwner(appOwner);
            requestPublisherDTO.setUserIp(clientIp);
            requestPublisherDTO.setApplicationConsumerKey(infoDTO.getConsumerKey());
            //context will always be empty as this method will call only for WebSocketFrame and url is null
            requestPublisherDTO.setApiContext(fullRequestUri);
            requestPublisherDTO.setThrottledOut(isThrottledOut);
            requestPublisherDTO.setApiHostname(DataPublisherUtil.getHostAddress());
            requestPublisherDTO.setApiMethod("-");
            requestPublisherDTO.setRequestTimestamp(requestTime);
            requestPublisherDTO.setApiResourcePath("-");
            requestPublisherDTO.setApiResourceTemplate("-");
            requestPublisherDTO.setUserAgent(useragent);
            requestPublisherDTO.setUsername(infoDTO.getEndUserName());
            requestPublisherDTO.setUserTenantDomain(tenantDomain);
            requestPublisherDTO.setApiTier(infoDTO.getTier());
            requestPublisherDTO.setApiVersion(version);
            requestPublisherDTO.setMetaClientType(keyType);
            requestPublisherDTO.setCorrelationID(correlationID);
            requestPublisherDTO.setUserAgent(useragent);
            requestPublisherDTO.setCorrelationID(correlationID);
            requestPublisherDTO.setGatewayType(APIMgtGatewayConstants.GATEWAY_TYPE);
            requestPublisherDTO.setLabel(APIMgtGatewayConstants.SYNAPDE_GW_LABEL);
            requestPublisherDTO.setProtocol("WebSocket");
            requestPublisherDTO.setDestination("-");
            requestPublisherDTO.setBackendTime(0);
            requestPublisherDTO.setResponseCacheHit(false);
            requestPublisherDTO.setResponseCode(0);
            requestPublisherDTO.setResponseSize(0);
            requestPublisherDTO.setServiceTime(0);
            requestPublisherDTO.setResponseTime(0);
            ExecutionTimeDTO executionTime = new ExecutionTimeDTO();
            executionTime.setBackEndLatency(0);
            executionTime.setOtherLatency(0);
            executionTime.setRequestMediationLatency(0);
            executionTime.setResponseMediationLatency(0);
            executionTime.setSecurityLatency(0);
            executionTime.setThrottlingLatency(0);
            requestPublisherDTO.setExecutionTime(executionTime);
            usageDataPublisher.publishEvent(requestPublisherDTO);
        } catch (Exception e) {
            // flow should not break if event publishing failed
            log.error("Cannot publish event. " + e.getMessage(), e);
        }

    }

    /*
     * Publish throttle events.
     */
    private void publishThrottleEvent() {
        long requestTime = System.currentTimeMillis();
        String correlationID = UUID.randomUUID().toString();
        try {
            ThrottlePublisherDTO throttlePublisherDTO = new ThrottlePublisherDTO();
            throttlePublisherDTO.setKeyType(infoDTO.getType());
            throttlePublisherDTO.setTenantDomain(tenantDomain);
            //throttlePublisherDTO.setApplicationConsumerKey(infoDTO.getConsumerKey());
            throttlePublisherDTO.setApiname(infoDTO.getApiName());
            throttlePublisherDTO.setVersion(infoDTO.getApiName() + ':' + version);
            throttlePublisherDTO.setContext(fullRequestUri);
            throttlePublisherDTO.setApiCreator(infoDTO.getApiPublisher());
            throttlePublisherDTO.setApiCreatorTenantDomain(MultitenantUtils.getTenantDomain(infoDTO.getApiPublisher()));
            throttlePublisherDTO.setApplicationName(infoDTO.getApplicationName());
            throttlePublisherDTO.setApplicationId(infoDTO.getApplicationId());
            throttlePublisherDTO.setSubscriber(infoDTO.getSubscriber());
            throttlePublisherDTO.setThrottledTime(requestTime);
            throttlePublisherDTO.setGatewayType(APIMgtGatewayConstants.GATEWAY_TYPE);
            throttlePublisherDTO.setThrottledOutReason("-");
            throttlePublisherDTO.setUsername(infoDTO.getEndUserName());
            throttlePublisherDTO.setCorrelationID(correlationID);
            throttlePublisherDTO.setHostName(DataPublisherUtil.getHostAddress());
            throttlePublisherDTO.setAccessToken("-");
            usageDataPublisher.publishEvent(throttlePublisherDTO);
        } catch (Exception e) {
            // flow should not break if event publishing failed
            log.error("Cannot publish event. " + e.getMessage(), e);
        }
    }

    private void removeTokenFromQuery(Map<String, List<String>> parameters) {
        StringBuilder queryBuilder = new StringBuilder(fullRequestUri.substring(0, fullRequestUri.indexOf('?') + 1));

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!APIConstants.AUTHORIZATION_QUERY_PARAM_DEFAULT.equals(entry.getKey())) {
                queryBuilder.append(entry.getKey()).append('=').append(entry.getValue().get(0)).append('&');
            }
        }

        // remove trailing '?' or '&' from the built string
        fullRequestUri = queryBuilder.substring(0, queryBuilder.length() - 1);
    }

    private SignedJWTInfo getSignedJwtInfo(String accessToken) throws ParseException {

        String signature = accessToken.split("\\.")[2];
        SignedJWTInfo signedJWTInfo = null;
        Cache gatewaySignedJWTParseCache = CacheProvider.getGatewaySignedJWTParseCache();
        if (gatewaySignedJWTParseCache != null) {
            Object cachedEntry = gatewaySignedJWTParseCache.get(signature);
            if (cachedEntry != null) {
                signedJWTInfo = (SignedJWTInfo) cachedEntry;
            }
            if (signedJWTInfo == null || !signedJWTInfo.getToken().equals(accessToken)) {
                SignedJWT signedJWT = SignedJWT.parse(accessToken);
                JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
                signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
                gatewaySignedJWTParseCache.put(signature, signedJWTInfo);
            }
        } else {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
        }
        return signedJWTInfo;
    }

    private MessageContext getMessageContext(String tenantDomain) throws AxisFault, URISyntaxException {

        MessageContext synCtx = WebsocketUtil.getSynapseMessageContext(tenantDomain);
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        msgCtx.setIncomingTransportName(new URI(fullRequestUri).getScheme());
        msgCtx.setProperty(Constants.Configuration.TRANSPORT_IN_URL, fullRequestUri);
        return synCtx;
    }

    private String getMatchingResource(ChannelHandlerContext ctx, MessageContext synCtx, FullHttpRequest req)
            throws WebSocketApiException {

        API selectedApi = getApi(fullRequestUri, synCtx);
        if (selectedApi == null) {
            handleError(ctx, "No matching API found to dispatch the request");
            return null;
        }
        if (StringUtils.EMPTY.equals(selectedApi.getVersion())) {
            log.info("Call to default Api");
            // this is a call to default api
            findAndUpdateApiName();
            selectedApi = synCtx.getConfiguration().getAPI(apiName);
            if (selectedApi == null) {
                handleError(ctx, "API missing for default version");
                return null;
            }
            reConstructFullUriWithVersion(selectedApi.getContext(), req, synCtx);
        }

        api = selectedApi;
        apiContext = selectedApi.getContext();
        //        synCtx.setProperty(RESTConstants.SYNAPSE_REST_API, apiName);
        //        synCtx.setProperty(RESTConstants.PROCESSED_API, selectedApi);

        Resource selectedResource = null;
        Utils.setSubRequestPath(selectedApi, synCtx);
        Set<Resource> acceptableResources = new LinkedHashSet<>(Arrays.asList(selectedApi.getResources()));
        if (!acceptableResources.isEmpty()) {
            for (RESTDispatcher dispatcher : ApiUtils.getDispatchers()) {
                Resource resource = dispatcher.findResource(synCtx, acceptableResources);
                if (resource != null) {
                    selectedResource = resource;
                    break;
                }
            }
        }
        if (selectedResource == null) {
            handleError(ctx, "No matching resource found to dispatch the request");
        }
        String resource = selectedResource.getDispatcherHelper().getString();
        log.info("Selected resource " + resource); // todo - make as debug
        return resource;
    }

    private void findAndUpdateApiName() {

        version = apiName.substring(apiName.lastIndexOf('_') + 2); // depends on naming convention of default api
        log.info("Version of default corresponding api :" + version);
        apiName = apiName.substring(0, apiName.length() - version.length() - 2) + ":v" + version;
        log.info("New APi name : " + apiName);
    }

    private void reConstructFullUriWithVersion(String apiContext , FullHttpRequest req, MessageContext synCtx){

        log.info("full request uri " + fullRequestUri);
        StringBuilder newUrl = new StringBuilder();
        int versionInsertionIndex = apiContext.split(URL_SEPARATOR).length - 2;
        if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            versionInsertionIndex += 2;
        }
        String[] uriParts = fullRequestUri.split(URL_SEPARATOR);
        for (int index = 0; index < uriParts.length; index++) {
            newUrl.append(URL_SEPARATOR);
            newUrl.append(uriParts[index]);
            if (index == versionInsertionIndex) {
                newUrl.append(URL_SEPARATOR);
                newUrl.append(version);
            }
        }
        // updating url for request dispatch
        fullRequestUri = newUrl.toString().substring(1);  // removing additional '/' at the beginning
        req.setUri(fullRequestUri);
        org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        axis2MsgCtx.setProperty(Constants.Configuration.TRANSPORT_IN_URL, fullRequestUri);
        log.info("new url : " + fullRequestUri);
    }

    private void handleError(ChannelHandlerContext ctx, String error) throws WebSocketApiException {

        log.error(error, new Throwable());
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                                    HttpResponseStatus.BAD_REQUEST,
                                                                    Unpooled.copiedBuffer(error, CharsetUtil.UTF_8));
        httpResponse.headers().set("content-type", "text/plain; charset=UTF-8");
        httpResponse.headers().set("content-length", httpResponse.content().readableBytes());
        ctx.writeAndFlush(httpResponse);
        throw new WebSocketApiException(error);
    }

    /**
     * Get the name of the matching api for the request path.
     *
     * @param requestPath The request path
     * @param synCtx      The Synapse Message Context
     * @return String The api name
     */
    private API getApi(String requestPath, MessageContext synCtx) {

        log.info("inbound name : " + inboundName);
        Collection<API> apis = synCtx.getEnvironment().getSynapseConfiguration().getAPIs(inboundName);
        for (API api : apis) {
            if (ApiUtils.matchApiPath(requestPath, api.getContext())) {
                apiName = api.getName();
                if (api.getVersionStrategy().getVersion() != null && !"".equals(api.getVersionStrategy().
                        getVersion())) {
                    apiName = apiName + ":v" + api.getVersionStrategy().getVersion();
                }
                version = api.getVersion();
                return api;
            }
        }
        return null;
    }
}
