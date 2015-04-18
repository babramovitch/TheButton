package com.nebulights.thebutton.events;

/**
 * Created by Ben on 10/04/2015.
 */
public class SetCurrentColorEvent {
    private int color;

    public SetCurrentColorEvent(int color) {
        this.color = color;
    }

    public int getColor() {
        return (color);
    }

}