package com.wonrui.zxinglite.utils;

import android.content.SharedPreferences;

import com.wonrui.zxinglite.preferences.Config;


/**
 * Enumerates settings of the preference controlling the front light.
 */
public enum FrontLightMode {
    /**
     * Always on.
     */
    ON,
    /**
     * On only when ambient light is low.
     */
    AUTO,
    /**
     * Always off.
     */
    OFF;

    public static FrontLightMode parse(String modeString) {
        return modeString == null ? OFF : valueOf(modeString);
    }

    public static FrontLightMode readPref(SharedPreferences sharedPrefs) {
        return parse(sharedPrefs.getString(Config.KEY_FRONT_LIGHT_MODE, OFF.toString()));
    }
}
