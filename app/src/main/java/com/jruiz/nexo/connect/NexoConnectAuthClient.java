package com.jruiz.nexo.connect;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Cliente de transporte para Supabase Auth OTP y el gateway restringido de Nexo Connect.
 *
 * Componente preliminar SIN pantalla ni activación. Usar exclusivamente desde un
 * hilo de trabajo (no UI). Las sesiones siguen en memoria; pendiente persistencia
 * cifrada con Android Keystore y manejo de cierre/revocación.
 *
 * Nunca registra ni imprime OTP, accessToken, refreshToken ni claves privadas.
 * La clave "apikey" del constructor debe ser la clave pública (publishable/anon)
 * de Supabase, NUNCA service_role.
 */
public final class NexoConnectAuthClient {
    private static final int MAX_REQUEST = 8 * 1024;
    private static final int MAX_RESPONSE = 64 * 1024;
    private static final int TIMEOUT_MS = 15000;
    private final String baseUrl;
    private final String publishableKey;

    public NexoConnectAuthClient(String projectUrl, String publishableKey) {
        if (projectUrl == null || publishableKey == null
                || publishableKey.trim().isEmpty())
            throw new IllegalArgumentException("Configuración de Nexo Connect incompleta");
        try {
            URL url = new URL(projectUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !url.getHost().matches("^[a-z0-9-]+\\.supabase\\.co$")
                    || url.getPort() != -1 || url.getUserInfo() != null
                    || (url.getPath() != null && !url.getPath().isEmpty()
                        && !"/".equals(url.getPath()))
                    || url.getQuery() != null || url.getRef() != null)
                throw new IllegalArgumentException("Servidor no permitido");
            baseUrl = "https://" + url.getHost();
        } catch (Exception e) {
            throw new IllegalArgumentException("Dirección Supabase inválida", e);
        }
        this.publishableKey = publishableKey;
    }

    /** El envío debe respetar los límites de solicitudes de Supabase Auth. */
    public void sendEmailCode(String email) throws Exception {
        String safeEmail = validatedEmail(email);
        JSONObject body = new JSONObject();
        body.put("email", safeEmail);
        body.put("create_user", true);
        request("/auth/v1/otp", body, null);
    }

    /**
     * Valida el código de seis cifras recibido por correo.
     * La llamada no registra automáticamente un nuevo dispositivo.
     */
    public Session verifyEmailCode(String email, String otp) throws Exception {
        String safeEmail = validatedEmail(email);
        if (otp == null || !otp.matches("^[0-9]{6,8}$"))
            throw new IllegalArgumentException("Código de verificación inválido");
        JSONObject body = new JSONObject();
        body.put("email", safeEmail);
        body.put("token", otp);
        body.put("type", "email");
        return parseSession(request("/auth/v1/verify", body, null));
    }

    public Session refreshSession(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.length() < 8 ||
                refreshToken.length() > 8192)
            throw new IllegalArgumentException("Sesión no válida");
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        return parseSession(request("/auth/v1/token?grant_type=refresh_token", body, null));
    }

    /** Consulta solo la generación e identificador del móvil principal. */
    public JSONObject deviceStatus(String accessToken) throws Exception {
        return request("/functions/v1/nexo-connect-gateway/status",
                new JSONObject(), validAccessToken(accessToken));
    }

    /** Nonce específico de acción, para una posterior solicitud firmada Ed25519. */
    public JSONObject requestChallenge(String accessToken, String deviceId,
                                       int generation, String action) throws Exception {
        if (deviceId == null ||
                !deviceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                || generation < 1 || action == null || !action.matches(
                "^(contact/(invite|accept|permissions|unlink)|key/verify|delivery/(send|resolve|request-resend))$"))
            throw new IllegalArgumentException("Dispositivo o acción no válida");
        JSONObject body = new JSONObject();
        body.put("deviceId", deviceId);
        body.put("generation", generation);
        body.put("action", action);
        return request("/functions/v1/nexo-connect-gateway/challenge",
                body, validAccessToken(accessToken));
    }

    private JSONObject request(String endpoint, JSONObject payload, String bearer)
            throws Exception {
        byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_REQUEST)
            throw new IllegalArgumentException("Solicitud demasiado grande");
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(baseUrl + endpoint).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("apikey", publishableKey);
            if (bearer != null)
                connection.setRequestProperty("Authorization", "Bearer " + bearer);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }
            int status = connection.getResponseCode();
            // Never follow redirects which could leak bearer or OTP to a new host.
            if (status < 200 || status >= 300)
                throw new TransportException(status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int amount;
                while ((amount = input.read(buffer)) != -1) {
                    if (amount == 0) continue;
                    if (output.size() + amount > MAX_RESPONSE)
                        throw new IllegalArgumentException("Respuesta excesiva");
                    output.write(buffer, 0, amount);
                }
                if (output.size() == 0) return new JSONObject();
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String validAccessToken(String token) {
        if (token == null || token.isEmpty() || token.length() > 16384
                || token.indexOf(' ') >= 0 || token.indexOf('\n') >= 0 ||
                token.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Token de acceso inválido");
        return token;
    }

    private static String validatedEmail(String input) {
        if (input == null) throw new IllegalArgumentException("Correo obligatorio");
        String email = input.trim();
        if (email.length() > 254 || !email.matches("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Correo no válido");
        return email;
    }

    private static Session parseSession(JSONObject response) {
        String access = response.optString("access_token", "");
        String refresh = response.optString("refresh_token", "");
        long expires = response.optLong("expires_in", 0);
        if (access.isEmpty() || refresh.isEmpty() || expires <= 0 || expires > 86400)
            throw new IllegalArgumentException("Sesión incompleta");
        return new Session(access, refresh, expires);
    }

    public static final class Session {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresInSeconds;
        private Session(String accessToken, String refreshToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresInSeconds = expiresInSeconds;
        }
        // No toString(): nunca registrar tokens ni volcarlos en la WebView.
    }

    public static final class TransportException extends Exception {
        public final int httpStatus;
        private TransportException(int status) {
            super("Servicio Nexo Connect temporalmente no disponible (HTTP " + status + ")");
            httpStatus = status;
        }
    }
}
