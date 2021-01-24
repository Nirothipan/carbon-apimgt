package org.wso2.carbon.apimgt.gateway.handlers.websocket;

public class WebSocketApiException extends Exception {

    public WebSocketApiException(String message) {
        super(message);
    }

    public WebSocketApiException( String message, Throwable cause) {
        super(message, cause);
    }

}
