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

package org.wso2.carbon.apimgt.gateway.handlers.streaming.websocket;

public class WebSocketApiConstants {

    WebSocketApiConstants() {
    }

    public static final String WEBSOCKET_DUMMY_HTTP_METHOD_NAME = "WS";
    public static final String WS_ENDPOINT_NAME = "WebSocketInboundEndpoint";
    public static final String WS_SECURED_ENDPOINT_NAME = "SecureWebSocketEP";
    public static final String URL_SEPARATOR = "/";
    public static final String DEFAULT_RESOURCE_NAME = "/_default_resource_of_api_";
}
