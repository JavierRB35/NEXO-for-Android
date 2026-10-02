package com.jruiz.nexo.input;

import android.content.Context;
import android.content.SharedPreferences;

public final class InputHubPrefs {
    private static final String PREFS = "nexo_input_hub";

    private InputHubPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean autoScanEnabled(Context c) { return prefs(c).getBoolean("auto_scan_enabled", false); }
    public static void setAutoScanEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("auto_scan_enabled", value).apply(); }

    public static boolean smsEnabled(Context c) { return prefs(c).getBoolean("sms_enabled", false); }
    public static void setSmsEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("sms_enabled", value).apply(); }
    public static boolean smsBaselined(Context c) { return prefs(c).getBoolean("sms_baselined", false); }
    public static void setSmsBaselined(Context c, boolean value) { prefs(c).edit().putBoolean("sms_baselined", value).apply(); }
    public static long smsLastSync(Context c) { return prefs(c).getLong("sms_last_sync", 0L); }
    public static void setSmsLastSync(Context c, long value) { prefs(c).edit().putLong("sms_last_sync", value).apply(); }
    public static String smsError(Context c) { return prefs(c).getString("sms_error", ""); }
    public static void setSmsError(Context c, String value) { prefs(c).edit().putString("sms_error", value == null ? "" : value).apply(); }

    public static boolean gmailEnabled(Context c) { return prefs(c).getBoolean("gmail_enabled", false); }
    public static void setGmailEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("gmail_enabled", value).apply(); }
    public static boolean gmailBaselined(Context c) { return prefs(c).getBoolean("gmail_baselined", false); }
    public static void setGmailBaselined(Context c, boolean value) { prefs(c).edit().putBoolean("gmail_baselined", value).apply(); }
    public static String gmailEmail(Context c) { return prefs(c).getString("gmail_email", ""); }
    public static void setGmailEmail(Context c, String value) { prefs(c).edit().putString("gmail_email", value == null ? "" : value).apply(); }
    public static String gmailError(Context c) { return prefs(c).getString("gmail_error", ""); }
    public static void setGmailError(Context c, String value) { prefs(c).edit().putString("gmail_error", value == null ? "" : value).apply(); }
    public static String gmailErrorDetail(Context c) { return prefs(c).getString("gmail_error_detail", ""); }
    public static void setGmailErrorDetail(Context c, String value) { prefs(c).edit().putString("gmail_error_detail", value == null ? "" : value).apply(); }
    public static long gmailLastSync(Context c) { return prefs(c).getLong("gmail_last_sync", 0L); }
    public static void setGmailLastSync(Context c, long value) { prefs(c).edit().putLong("gmail_last_sync", value).apply(); }
}
