package com.jruiz.nexo.weather;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Tiempo de cabecera: GPS actual o municipio elegido, nunca una ubicación inferida por Google. */
public final class HeaderWeather {
    public static final int REQUEST_LOCATION = 116;
    private static final String PREFS = "nexo_header_weather";
    private static final long CACHE_MS = 20 * 60 * 1000L;
    private static final long LOCATION_TIMEOUT_MS = 12000L;
    private static final long MAX_LOCATION_AGE_MS = 5 * 60 * 1000L;

    public interface Callback { void accept(JSONObject data); }

    private final Activity activity;
    private final Callback weatherCallback;
    private final Callback placesCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean closed, loading, gpsRequested;
    private int generation, searchGeneration;
    private long lastFetch;
    private JSONObject cachedWeather;
    private Location bestLocation, lastLocation;
    private LocationManager locationManager;
    private LocationListener gpsListener, networkListener;
    private CancellationSignal gpsCancellation, networkCancellation;
    private String lastDisplayName = "";

    public HeaderWeather(Activity activity, Callback weatherCallback, Callback placesCallback) {
        this.activity = activity;
        this.weatherCallback = weatherCallback;
        this.placesCallback = placesCallback;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean manual() {
        return "manual".equals(prefs().getString("mode", "automatic"));
    }

    private static String safeLabel(String s) {
        String v = s == null ? "" : s.trim().replaceAll("[\\r\\n\\t]+", " ");
        return v.length() <= 120 ? v : v.substring(0, 120);
    }

    public JSONObject getSettings() {
        JSONObject out = new JSONObject();
        try {
            out.put("mode", manual() ? "manual" : "automatic");
            out.put("manualLabel", prefs().getString("manual_label", ""));
            out.put("manualName", prefs().getString("manual_name", ""));
            out.put("hasFinePermission", activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            out.put("hasCoarsePermission", activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            out.put("lastName", lastDisplayName);
        } catch (Exception ignored) {}
        return out;
    }

    public void useAutomatic() {
        prefs().edit().putString("mode", "automatic").apply();
        activity.runOnUiThread(() -> {
            invalidate();
            refreshMain(true);
        });
    }

    public boolean chooseManual(String json) {
        try {
            JSONObject input = new JSONObject(json);
            String name = safeLabel(input.optString("name", ""));
            String label = safeLabel(input.optString("label", ""));
            double lat = input.getDouble("latitude");
            double lon = input.getDouble("longitude");
            if (name.length() < 2 || label.length() < 2 || !Double.isFinite(lat) ||
                    !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
                return false;
            prefs().edit()
                    .putString("mode", "manual")
                    .putString("manual_name", name)
                    .putString("manual_label", label)
                    .putString("manual_lat", String.valueOf(lat))
                    .putString("manual_lon", String.valueOf(lon))
                    .apply();
            activity.runOnUiThread(() -> {
                invalidate();
                refreshMain(false);
            });
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void refresh(boolean promptOnce) {
        activity.runOnUiThread(() -> refreshMain(promptOnce));
    }

    private void invalidate() {
        ++generation;
        loading = false;
        cachedWeather = null;
        lastFetch = 0;
        bestLocation = null;
        lastLocation = null;
        lastDisplayName = "";
        cancelLocation();
    }

    private void refreshMain(boolean promptOnce) {
        if (closed) return;
        if (cachedWeather != null && System.currentTimeMillis() - lastFetch < CACHE_MS) {
            weatherCallback.accept(cachedWeather);
            return;
        }
        if (loading) return;
        if (manual()) {
            SharedPreferences p = prefs();
            try {
                double lat = Double.parseDouble(p.getString("manual_lat", ""));
                double lon = Double.parseDouble(p.getString("manual_lon", ""));
                if (!Double.isFinite(lat) || !Double.isFinite(lon) ||
                        lat < -90 || lat > 90 || lon < -180 || lon > 180)
                    throw new IllegalArgumentException();
                String label = p.getString("manual_name", "");
                String details = p.getString("manual_label", "");
                if (label == null || label.trim().isEmpty()) throw new IllegalArgumentException();
                loadWeather(lat, lon, label, details, false, 0, ++generation);
            } catch (Exception e) {
                sendError("manual");
            }
            return;
        }
        boolean fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            sendError("permission");
            SharedPreferences p = prefs();
            if (promptOnce && !p.getBoolean("location_prompted", false)) {
                p.edit().putBoolean("location_prompted", true).apply();
                activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQUEST_LOCATION);
            }
            return;
        }
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) { sendError("location"); return; }
        bestLocation = null;
        gpsRequested = false;
        final int token = ++generation;
        loading = true;
        boolean requested = false;
        try {
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsRequested = true;
                requested = requestLocation(LocationManager.GPS_PROVIDER, token);
            }
            if ((fine || coarse) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                requested = requestLocation(LocationManager.NETWORK_PROVIDER, token) || requested;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (LocationManager.GPS_PROVIDER.equals(provider) && !fine) continue;
                Location recent = locationManager.getLastKnownLocation(provider);
                if (acceptable(recent)) considerLocation(token, recent, false);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        if (!requested) {
            loading = false;
            if (bestLocation != null) finishLocation(token); else sendError("location");
            return;
        }
        handler.postDelayed(() -> {
            if (closed || token != generation || !loading) return;
            finishLocation(token);
        }, LOCATION_TIMEOUT_MS);
    }

    private boolean requestLocation(String provider, int token) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                CancellationSignal signal = new CancellationSignal();
                if (LocationManager.GPS_PROVIDER.equals(provider)) gpsCancellation = signal;
                else networkCancellation = signal;
                locationManager.getCurrentLocation(provider, signal, activity.getMainExecutor(),
                        loc -> considerLocation(token, loc, true));
            } else {
                LocationListener listener = new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        considerLocation(token, location, true);
                    }
                };
                if (LocationManager.GPS_PROVIDER.equals(provider)) gpsListener = listener;
                else networkListener = listener;
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            }
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean acceptable(Location location) {
        if (location == null) return false;
        long age = System.currentTimeMillis() - location.getTime();
        return age >= -10000 && age <= MAX_LOCATION_AGE_MS &&
                Double.isFinite(location.getLatitude()) && Double.isFinite(location.getLongitude()) &&
                location.getLatitude() >= -90 && location.getLatitude() <= 90 &&
                location.getLongitude() >= -180 && location.getLongitude() <= 180 &&
                (!location.hasAccuracy() || location.getAccuracy() < 10000f);
    }

    private static float accuracy(Location l) {
        return l != null && l.hasAccuracy() ? l.getAccuracy() : 10000f;
    }

    private void considerLocation(int token, Location candidate, boolean current) {
        if (closed || token != generation || !loading || !acceptable(candidate)) return;
        if (bestLocation == null || accuracy(candidate) < accuracy(bestLocation) ||
                (accuracy(candidate) == accuracy(bestLocation) && candidate.getTime() > bestLocation.getTime()))
            bestLocation = new Location(candidate);
        if (current && (LocationManager.GPS_PROVIDER.equals(candidate.getProvider()) && accuracy(candidate) <= 500f ||
                !gpsRequested && accuracy(candidate) <= 1000f))
            finishLocation(token);
    }

    private void finishLocation(int token) {
        if (closed || token != generation || !loading) return;
        loading = false;
        cancelLocation();
        Location selected = bestLocation;
        if (selected == null) { sendError("location"); return; }
        lastLocation = new Location(selected);
        loadWeather(selected.getLatitude(), selected.getLongitude(), "", "", true,
                Math.round(accuracy(selected)), token);
    }

    private void cancelLocation() {
        if (locationManager != null) {
            try { if (gpsListener != null) locationManager.removeUpdates(gpsListener); } catch (Exception ignored) {}
            try { if (networkListener != null) locationManager.removeUpdates(networkListener); } catch (Exception ignored) {}
        }
        gpsListener = null;
        networkListener = null;
        if (gpsCancellation != null) { gpsCancellation.cancel(); gpsCancellation = null; }
        if (networkCancellation != null) { networkCancellation.cancel(); networkCancellation = null; }
    }

    private static String readResponse(HttpsURLConnection conn, int maxChars) throws Exception {
        if (conn.getResponseCode() != 200) throw new java.io.IOException("HTTP " + conn.getResponseCode());
        StringBuilder buffer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                if (buffer.length() > maxChars) throw new java.io.IOException("Response too large");
            }
        }
        return buffer.toString();
    }

    private String reverseName(double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return "";
            Geocoder geo = new Geocoder(activity.getApplicationContext(), new Locale("es", "ES"));
            @SuppressWarnings("deprecation")
            List<Address> results = geo.getFromLocation(lat, lon, 1);
            if (results == null || results.isEmpty()) return "";
            Address a = results.get(0);
            String locality = a.getLocality();
            if (locality == null || locality.isBlank()) locality = a.getSubLocality();
            return safeLabel(locality);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void loadWeather(double latitude, double longitude, String manualName,
                             String detailName, boolean automatic, long accuracyMeters, int token) {
        loading = true;
        new Thread(() -> {
            HttpsURLConnection conn = null;
            try {
                String endpoint = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code,is_day&timezone=auto",
                        latitude, longitude);
                conn = (HttpsURLConnection) new URL(endpoint).openConnection();
                conn.setConnectTimeout(7500);
                conn.setReadTimeout(7500);
                conn.setRequestProperty("Accept", "application/json");
                JSONObject current = new JSONObject(readResponse(conn, 32768)).getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                if (!Double.isFinite(temperature)) throw new java.io.IOException("Invalid temperature");
                String name = automatic && accuracyMeters > 3000 ? "Ubicación aproximada"
                        : automatic ? reverseName(latitude, longitude) : safeLabel(manualName);
                if (name.isBlank()) name = "Ubicación actual";
                JSONObject data = new JSONObject();
                data.put("ok", true);
                data.put("temperature", temperature);
                data.put("code", current.getInt("weather_code"));
                data.put("isDay", current.optInt("is_day", 1) == 1);
                data.put("name", name);
                data.put("mode", automatic ? "automatic" : "manual");
                data.put("approximate", automatic && accuracyMeters > 1000);
                data.put("accuracyMeters", automatic ? accuracyMeters : 0);
                data.put("detailName", automatic ? name : safeLabel(detailName));
                handler.post(() -> {
                    if (closed || token != generation) return;
                    loading = false;
                    cachedWeather = data;
                    lastFetch = System.currentTimeMillis();
                    lastDisplayName = data.optString("detailName", data.optString("name", ""));
                    weatherCallback.accept(data);
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

    public void searchPlaces(String query) {
        String q = query == null ? "" : query.trim();
        int token = ++searchGeneration;
        if (q.length() < 2 || q.length() > 100) {
            sendPlacesError("Escribe al menos dos letras de la población.");
            return;
        }
        new Thread(() -> {
            HttpsURLConnection conn = null;
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                        URLEncoder.encode(q, "UTF-8") + "&count=8&language=es&format=json";
                conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(7500);
                conn.setReadTimeout(7500);
                conn.setRequestProperty("Accept", "application/json");
                JSONObject answer = new JSONObject(readResponse(conn, 65536));
                JSONArray source = answer.optJSONArray("results");
                JSONArray results = new JSONArray();
                if (source != null) for (int i = 0; i < source.length(); i++) {
                    JSONObject p = source.optJSONObject(i);
                    if (p == null) continue;
                    String name = safeLabel(p.optString("name", ""));
                    if (name.isEmpty()) continue;
                    String admin1 = safeLabel(p.optString("admin1", ""));
                    String country = safeLabel(p.optString("country", ""));
                    String label = name;
                    if (!admin1.isBlank() && !name.equalsIgnoreCase(admin1)) label += ", " + admin1;
                    if (!country.isBlank()) label += ", " + country;
                    JSONObject row = new JSONObject();
                    row.put("name", name);
                    row.put("label", label);
                    row.put("latitude", p.getDouble("latitude"));
                    row.put("longitude", p.getDouble("longitude"));
                    results.put(row);
                }
                JSONObject response = new JSONObject().put("ok", true).put("places", results);
                handler.post(() -> { if (!closed && token == searchGeneration) placesCallback.accept(response); });
            } catch (Exception ignored) {
                handler.post(() -> {
                    if (!closed && token == searchGeneration)
                        sendPlacesError("No se pudo buscar la población. Revisa tu conexión.");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "nexo-weather-places").start();
    }

    private void sendPlacesError(String msg) {
        try {
            placesCallback.accept(new JSONObject().put("ok", false).put("error", msg));
        } catch (Exception ignored) {}
    }

    private void sendError(String reason) {
        try {
            weatherCallback.accept(new JSONObject().put("ok", false).put("reason", reason)
                    .put("mode", manual() ? "manual" : "automatic")
                    .put("name", manual() ? prefs().getString("manual_name", "") : "Ubicación no disponible"));
        } catch (Exception ignored) {}
    }

    public void onLocationPermissionResult(int[] results) {
        for (int r : results) {
            if (r == PackageManager.PERMISSION_GRANTED) { refresh(false); return; }
        }
        sendError("permission");
    }

    /** Abrir el tiempo de la localidad identificada, nunca coordenadas redondeadas o ambiguas. */
    public void openDetails() {
        activity.runOnUiThread(() -> {
            String place = lastDisplayName;
            if (place.isBlank() && manual()) place = prefs().getString("manual_label", "");
            if (place.isBlank() || place.equals("Ubicación aproximada") || place.equals("Ubicación actual"))
                place = "cerca de mí";
            Uri url = Uri.parse("https://www.google.com/search?q=" +
                    Uri.encode("el tiempo en " + place));
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, url)); }
            catch (Exception e) {
                Toast.makeText(activity, "No hay un navegador disponible", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void close() {
        closed = true;
        ++generation;
        ++searchGeneration;
        cancelLocation();
        handler.removeCallbacksAndMessages(null);
    }
}
