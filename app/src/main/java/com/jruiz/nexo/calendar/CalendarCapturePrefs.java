package com.jruiz.nexo.calendar;

import android.content.Context;
import android.content.SharedPreferences;

public final class CalendarCapturePrefs {
    private static final String PREFS = "nexo_calendar_capture";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BASELINED = "baselined";

    private CalendarCapturePrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) setBaselined(context, false);
    }

    public static boolean isBaselined(Context context) {
        return prefs(context).getBoolean(KEY_BASELINED, false);
    }

    public static void setBaselined(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_BASELINED, value).apply();
    }
}
