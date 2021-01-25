package org.wso2.carbon.apimgt.gateway.handlers.websocket;

import org.apache.synapse.MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.wso2.carbon.apimgt.gateway.handlers.security.APIAuthenticationHandler;


// todo remove this.
public class WebSocketAuntheticationHandler extends APIAuthenticationHandler {

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        boolean prop = true;
        if ( prop){
           // do something
           return true;
        } else {
            return super.handleRequest(messageContext);
        }
    }

}
