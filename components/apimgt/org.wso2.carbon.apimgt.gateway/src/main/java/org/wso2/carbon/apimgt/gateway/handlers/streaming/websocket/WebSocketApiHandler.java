///*
// *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
// *
// *  WSO2 Inc. licenses this file to you under the Apache License,
// *  Version 2.0 (the "License"); you may not use this file except
// *  in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *    http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing,
// * software distributed under the License is distributed on an
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// * KIND, either express or implied.  See the License for the
// * specific language governing permissions and limitations
// * under the License.
// */
//
//package org.wso2.carbon.apimgt.gateway.handlers.streaming.websocket;
//
//public class WebSocketApiHandler extends AbstractHandler {
//
//    public boolean handleRequest(MessageContext messageContext) {
//
//        String apiContext = (String) messageContext.getProperty(RESTConstants.REST_API_CONTEXT);
//        String apiVersion = (String) messageContext.getProperty(RESTConstants.SYNAPSE_REST_API_VERSION);
//        String httpMethod = (String) ((Axis2MessageContext) messageContext).getAxis2MessageContext().
//                getProperty(Constants.Configuration.HTTP_METHOD);
//        API selectedApi = Utils.getSelectedAPI(messageContext);
//        org.apache.axis2.context.MessageContext axis2MC =
//                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
//        Map headers = (Map) axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
//        String corsRequestMethod = (String) headers.get(APIConstants.CORSHeaders.ACCESS_CONTROL_REQUEST_METHOD);
//
//        Resource selectedResource = null;
//        Utils.setSubRequestPath(selectedApi, messageContext);
//
//        if (selectedApi != null) {
//            Resource[] allAPIResources = selectedApi.getResources();
//            Set<Resource> acceptableResources = new LinkedHashSet<>();
//
//            for (Resource resource : allAPIResources) {
//                //If the requesting method is OPTIONS or if the Resource contains the requesting method
//                if ((RESTConstants.METHOD_OPTIONS.equals(httpMethod) && resource.getMethods() != null && Arrays.asList(
//                        resource.getMethods()).contains(corsRequestMethod)) || (resource.getMethods() != null && Arrays
//                        .asList(resource.getMethods()).contains(httpMethod))) {
//                    acceptableResources.add(resource);
//                }
//            }
//
//            if (!acceptableResources.isEmpty()) {
//                for (RESTDispatcher dispatcher : RESTUtils.getDispatchers()) {
//                    Resource resource = dispatcher.findResource(messageContext, acceptableResources);
//                    if (resource != null) {
//                        selectedResource = resource;
//                        break;
//                    }
//                }
//                if (selectedResource == null) {
//                    handleResourceNotFound(messageContext, Arrays.asList(allAPIResources));
//                    return false;
//                }
//            }
//            //If no acceptable resources are found
//            else {
//                //We're going to send a 405 or a 404. Run the following logic to determine which.
//                handleResourceNotFound(messageContext, Arrays.asList(allAPIResources));
//                return false;
//            }
//
//            //No matching resource found
//            if (selectedResource == null) {
//                //Respond with a 404
//                onResourceNotFoundError(messageContext, HttpStatus.SC_NOT_FOUND,
//                                        APIMgtGatewayConstants.RESOURCE_NOT_FOUND_ERROR_MSG);
//                return false;
//            }
//        }
//
//        String resourceString = selectedResource.getDispatcherHelper().getString();
//        String resourceCacheKey = APIUtil.getResourceInfoDTOCacheKey(apiContext, apiVersion, resourceString,
//                                                                     httpMethod);
//        messageContext.setProperty(APIConstants.API_ELECTED_RESOURCE, resourceString);
//        messageContext.setProperty(APIConstants.API_RESOURCE_CACHE_KEY, resourceCacheKey);
//    }
//
//    protected String getFullRequestPath(MessageContext messageContext) {
//        return RESTUtils.getFullRequestPath(messageContext);
//    }
//
//    public boolean handleResponse(MessageContext messageContext) {
//
//        return true;
//    }
//
//    private void handleResourceNotFound(MessageContext messageContext, List<Resource> allAPIResources) {
//
//        Resource uriMatchingResource = null;
//
//        for (RESTDispatcher dispatcher : RESTUtils.getDispatchers()) {
//            uriMatchingResource = dispatcher.findResource(messageContext, allAPIResources);
//            //If a resource with a matching URI was found.
//            if (uriMatchingResource != null) {
//                onResourceNotFoundError(messageContext, HttpStatus.SC_METHOD_NOT_ALLOWED,
//                                        APIMgtGatewayConstants.METHOD_NOT_FOUND_ERROR_MSG);
//                return;
//            }
//        }
//
//        //If a resource with a matching URI was not found.
//        //Respond with a 404.
//        onResourceNotFoundError(messageContext, HttpStatus.SC_NOT_FOUND,
//                                APIMgtGatewayConstants.RESOURCE_NOT_FOUND_ERROR_MSG);
//    }
//
//    private void onResourceNotFoundError(MessageContext messageContext, int statusCode, String errorMessage) {
//
//        messageContext.setProperty(APIConstants.CUSTOM_HTTP_STATUS_CODE, statusCode);
//        messageContext.setProperty(APIConstants.CUSTOM_ERROR_CODE, statusCode);
//        messageContext.setProperty(APIConstants.CUSTOM_ERROR_MESSAGE, errorMessage);
//        Mediator resourceMisMatchedSequence = messageContext.getSequence(RESTConstants.NO_MATCHING_RESOURCE_HANDLER);
//        if (resourceMisMatchedSequence != null) {
//            resourceMisMatchedSequence.mediate(messageContext);
//        }
//    }
//
//}
