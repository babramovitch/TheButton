package com.nebulights.thebutton.events;

import com.google.gson.JsonObject;

/**
 * Created by Ben on 05/04/2015.
 */
public class CurrentTimeEvent {

    JsonObject currentPing;

    public CurrentTimeEvent(JsonObject currentPing) {
        this.currentPing = currentPing;
    }

    public JsonObject getCurrentTime() {
        return currentPing;
    }


}
