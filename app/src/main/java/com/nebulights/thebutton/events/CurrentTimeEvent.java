package com.nebulights.thebutton.events;

/**
 * Created by Ben on 05/04/2015.
 */
public class CurrentTimeEvent {

    String currentPing;

    public CurrentTimeEvent(String currentPing) {
        this.currentPing = currentPing;
    }

    public String getCurrentTime() {
        return currentPing;
    }


}
