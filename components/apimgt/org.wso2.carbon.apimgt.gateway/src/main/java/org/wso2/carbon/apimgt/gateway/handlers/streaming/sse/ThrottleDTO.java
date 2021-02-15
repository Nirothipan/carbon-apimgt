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

import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;

import java.util.List;
import java.util.Map;

/**
 * Holder for throttling data.
 */
public class ThrottleDTO {

    private String applicationTier;
    private boolean authorized;
    private String subscriber;
    private String tier;
    private String type;
    //isContentAware property is here to notify if there is at least one content based tier associated with request
    //If this property is true then throttle handler should build message or get content length and pass it to
    //throttle server.
    private boolean contentAware;
    //Form API Manager 2.0 onward API specific tiers can define and this property is here to pass it.
    private String apiTier;
    //JWT or SAML token containing details of API invoker
    private String userType;
    private String endUserToken;
    private String endUserName;
    private String applicationId;
    private String applicationName;
    //use this to pass key validation status
    private int validationStatus;
    private long validityPeriod;
    private long issuedTime;
    private List<String> authorizedDomains;
    //Following throttle data list can be use to hold throttle data and api level throttle key
    //should be its first element.
    private List<String> throttlingDataList;
    private int spikeArrestLimit;
    private String subscriberTenantDomain;
    private String spikeArrestUnit;
    private boolean stopOnQuotaReach;
    //keeps productId of product for which the key was validated, if key was validated for an api this will be null
    private String productName;
    private String productProvider;
    private String keyManager;
    private String apiVersion;
    private String applicationUUID;
    private Map<String, String> appAttributes;

    private String apiName;
    private String apiContext;
    private String remtoeIp;
    private String resourceLevelThrottleKey;

    public ThrottleDTO(AuthenticationContext context, String apiContext, String version, String resourceLevelThrottleKey) {

        //this.authenticationContext = context;
        this.applicationTier = context.getApplicationTier();
        this.authorized = context.isAuthenticated();
        this.tier = context.getTier();
        this.subscriberTenantDomain = context.getSubscriberTenantDomain();
        this.subscriber = context.getSubscriber();
        this.stopOnQuotaReach = context.isStopOnQuotaReach();
        this.apiName = context.getApiName();
        this.applicationId = context.getApplicationId();
        this.type = context.getKeyType();
        this.applicationName = context.getApplicationName();
        this.endUserName = context.getUsername();
        this.apiTier = context.getApiTier();
        this.apiVersion = version;
        this.apiContext = apiContext;
        this.resourceLevelThrottleKey = resourceLevelThrottleKey;
    }

    public String getApplicationTier() {
        return applicationTier;
    }

    public void setApplicationTier(String applicationTier) {
        this.applicationTier = applicationTier;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public String getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(String subscriber) {
        this.subscriber = subscriber;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isContentAware() {
        return contentAware;
    }


    public String getApiTier() {
        return apiTier;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getSubscriberTenantDomain() {
        return subscriberTenantDomain;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiContext() {
        return apiContext;
    }

    public void setApiContext(String apiContext) {
        this.apiContext = apiContext;
    }

    public String getRemtoeIp() {
        return "192.168.8.100"; // todo //remtoeIp;
    }

  public String getResourceLevelThrottleKey() {
        return resourceLevelThrottleKey;
    }

}
