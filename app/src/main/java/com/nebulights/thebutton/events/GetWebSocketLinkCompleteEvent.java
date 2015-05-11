package com.nebulights.thebutton.events;

import java.net.URI;

/**
 * Created by Ben on 05/04/2015.
 */
public class GetWebSocketLinkCompleteEvent {

    URI webSocketLink;

    public GetWebSocketLinkCompleteEvent(URI webSocketLink) {
        this.webSocketLink = webSocketLink;
    }

    public URI getWebSocketLink() {
        return webSocketLink;
    }

}
