package com.nebulights.thebutton.events;

/**
 * Created by Ben on 05/04/2015.
 */
public class ShutDownEvent {

     boolean fullshutdown = false;

    public ShutDownEvent(boolean fullshutdown) {
        this.fullshutdown = fullshutdown;
    }

    public boolean isFullshutdown() {
        return fullshutdown;
    }

}
