package com.nebulights.thebutton;

import android.content.Context;

/**
 * Created by Ben on 11/04/2015.
 */
public class  ButtonColors {

    static int[] buttonColors = {0xFF820080, 0xFF0083C7, 0xFF02be01, 0xFFE5D900,0xFFe59500,0xFFe50000 };

    public static int getButtonColor(int time) {

        if (isBetween(time, 60, 52)) {
            return buttonColors[0];
        }else if (isBetween(time, 51, 42)) {
            return buttonColors[1];
        }else if (isBetween(time, 41, 32)) {
            return buttonColors[2];
        }else if (isBetween(time, 31, 22)) {
            return buttonColors[3];
        }else if (isBetween(time, 21, 12)) {
            return buttonColors[4];
        }else if (isBetween(time, 11, 0)) {
            return buttonColors[5];
        }

        return buttonColors[0];

    }

    private static boolean isBetween(int x, int upper, int lower) {
        return lower <= x && x <= upper;
    }

}
