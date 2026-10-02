package com.jruiz.nexo.communications;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CommunicationCapturePrefs {
    private static final String PREFS = "nexo_communication_capture";
    private static final String KEY_ENABLED = "enabled";

    private static final LinkedHashMap<String,String[]> APP_PACKAGES = new LinkedHashMap<>();
    static {
        APP_PACKAGES.put("whatsapp", new String[]{"com.whatsapp","com.whatsapp.w4b"});
        APP_PACKAGES.put("gmail", new String[]{"com.google.android.gm"});
        APP_PACKAGES.put("telegram", new String[]{"org.telegram.messenger","org.telegram.messenger.web"});
        APP_PACKAGES.put("sms", new String[]{
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.android.mms"
        });
    }

    private CommunicationCapturePrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isAppEnabled(Context context, String key) {
        if (key == null || !APP_PACKAGES.containsKey(key)) return false;
        return prefs(context).getBoolean("app_" + key, true);
    }

    public static void setAppEnabled(Context context, String key, boolean enabled) {
        if (key == null || !APP_PACKAGES.containsKey(key)) return;
        prefs(context).edit().putBoolean("app_" + key, enabled).apply();
    }

    public static String keyForPackage(String packageName) {
        if (packageName == null) return "";
        for (Map.Entry<String,String[]> e : APP_PACKAGES.entrySet()) {
            for (String p : e.getValue()) if (packageName.equals(p)) return e.getKey();
        }
        return "";
    }

    public static boolean isPackageEnabled(Context context, String packageName) {
        String key = keyForPackage(packageName);
        return !key.isBlank() && isEnabled(context) && isAppEnabled(context, key);
    }

    public static String labelForKey(String key) {
        if ("whatsapp".equals(key)) return "WhatsApp";
        if ("gmail".equals(key)) return "Gmail";
        if ("telegram".equals(key)) return "Telegram";
        if ("sms".equals(key)) return "SMS";
        return key == null ? "" : key;
    }

    public static JSONObject toJson(Context context) {
        JSONObject out = new JSONObject();
        try {
            out.put("enabled", isEnabled(context));
            JSONArray apps = new JSONArray();
            for (String key : APP_PACKAGES.keySet()) {
                JSONObject a = new JSONObject();
                a.put("key", key);
                a.put("label", labelForKey(key));
                a.put("enabled", isAppEnabled(context, key));
                apps.put(a);
            }
            out.put("apps", apps);
        } catch (Exception ignored) {}
        return out;
    }
}
