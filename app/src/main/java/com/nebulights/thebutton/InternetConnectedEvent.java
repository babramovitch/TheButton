package com.nebulights.thebutton;

/**
 * Created by Ben on 05/04/2015.
 */
public class InternetConnectedEvent {

    boolean connected = false;

    public InternetConnectedEvent(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }

}
