package com.jruiz.nexo.weather;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Consulta opcional del tiempo; no utiliza la red dentro de la WebView. */
public final class HeaderWeather {
    public static final int REQUEST_LOCATION = 116;
    private static final long CACHE_MS = 20 * 60 * 1000L;
    private static final long LOCATION_TIMEOUT_MS = 12000L;

    public interface Callback { void onWeather(JSONObject data); }

    private final Activity activity;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean closed = false;
    private boolean loading = false;
    private int generation = 0;
    private long lastFetch = 0;
    private JSONObject cachedWeather;
    private Location lastLocation;
    private LocationListener pendingListener;
    private LocationManager locationManager;

    public HeaderWeather(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void refresh(boolean promptOnce) {
        activity.runOnUiThread(() -> refreshMain(promptOnce));
    }

    private void refreshMain(boolean promptOnce) {
        if (closed) return;
        boolean fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            sendError("permission");
            SharedPreferences prefs = activity.getSharedPreferences("nexo_header_weather", Context.MODE_PRIVATE);
            if (promptOnce && !prefs.getBoolean("location_prompted", false)) {
                prefs.edit().putBoolean("location_prompted", true).apply();
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            }
            return;
        }
        if (cachedWeather != null && System.currentTimeMillis() - lastFetch < CACHE_MS) {
            callback.onWeather(cachedWeather);
            return;
        }
        if (loading) return;
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) { sendError("location"); return; }
        Location recent = null;
        String provider = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                provider = LocationManager.NETWORK_PROVIDER;
            else if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                provider = LocationManager.GPS_PROVIDER;
            if (provider != null) recent = locationManager.getLastKnownLocation(provider);
        } catch (SecurityException | IllegalArgumentException ignored) { }
        if (recent != null && recent.getTime() > System.currentTimeMillis() - 30 * 60 * 1000L) {
            loadWeather(recent, ++generation);
            return;
        }
        if (provider == null) {
            if (recent != null && recent.getTime() > System.currentTimeMillis() - 6 * 60 * 60 * 1000L)
                loadWeather(recent, ++generation);
            else sendError("location");
            return;
        }
        loading = true;
        final int token = ++generation;
        final Location fallback = recent != null && recent.getTime() > System.currentTimeMillis() - 6 * 60 * 60 * 1000L ? recent : null;
        handler.postDelayed(() -> onLocation(token, fallback), LOCATION_TIMEOUT_MS);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                locationManager.getCurrentLocation(provider, null, activity.getMainExecutor(), loc -> onLocation(token, loc == null ? fallback : loc));
            } else {
                pendingListener = new LocationListener() {
                    @Override public void onLocationChanged(Location loc) { onLocation(token, loc); }
                };
                locationManager.requestSingleUpdate(provider, pendingListener, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException ex) {
            onLocation(token, fallback);
        }
    }

    private void onLocation(int token, Location location) {
        if (closed || token != generation || !loading) return;
        removeListener();
        if (location == null) { loading = false; sendError("location"); return; }
        loadWeather(location, token);
    }

    private void removeListener() {
        if (locationManager != null && pendingListener != null) {
            try { locationManager.removeUpdates(pendingListener); } catch (Exception ignored) { }
        }
        pendingListener = null;
    }

    private void loadWeather(Location location, int token) {
        lastLocation = location;
        loading = true;
        final double latitude = Math.round(location.getLatitude() * 100.0) / 100.0;
        final double longitude = Math.round(location.getLongitude() * 100.0) / 100.0;
        new Thread(() -> {
            HttpsURLConnection conn = null;
            try {
                String endpoint = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&timezone=auto",
                    latitude, longitude);
                conn = (HttpsURLConnection) new URL(endpoint).openConnection();
                conn.setConnectTimeout(7500);
                conn.setReadTimeout(7500);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200) throw new java.io.IOException("Weather HTTP error");
                StringBuilder buffer = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line);
                        if (buffer.length() > 32768) throw new java.io.IOException("Weather response too large");
                    }
                }
                JSONObject current = new JSONObject(buffer.toString()).getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                if (!Double.isFinite(temperature)) throw new java.io.IOException("Invalid temperature");
                JSONObject result = new JSONObject();
                result.put("ok", true);
                result.put("temperature", temperature);
                result.put("code", current.getInt("weather_code"));
                result.put("isDay", current.optInt("is_day", 1) == 1);
                handler.post(() -> {
                    if (closed || token != generation) return;
                    cachedWeather = result;
                    lastFetch = System.currentTimeMillis();
                    loading = false;
                    callback.onWeather(result);
                });
            } catch (Exception ignored) {
                handler.post(() -> {
                    if (closed || token != generation) return;
                    loading = false;
                    sendError("network");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "nexo-header-weather").start();
    }

    private void sendError(String reason) {
        try {
            callback.onWeather(new JSONObject().put("ok", false).put("reason", reason));
        } catch (Exception ignored) { }
    }

    public void onLocationPermissionResult(int[] results) {
        for (int r : results) {
            if (r == PackageManager.PERMISSION_GRANTED) { refresh(false); return; }
        }
        sendError("permission");
    }

    /** Enlace a la previsión extendida en el navegador, nunca dentro de Nexo. */
    public void openDetails() {
        activity.runOnUiThread(() -> {
            String search;
            if (lastLocation != null) {
                search = String.format(Locale.US, "el tiempo en %.2f, %.2f",
                    lastLocation.getLatitude(), lastLocation.getLongitude());
            } else {
                search = "el tiempo cerca de mí";
            }
            Uri url = Uri.parse("https://www.google.com/search?q=" + Uri.encode(search));
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, url)); }
            catch (Exception e) {
                Toast.makeText(activity, "No hay un navegador disponible", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void close() {
        closed = true;
        ++generation;
        removeListener();
        handler.removeCallbacksAndMessages(null);
    }
}
