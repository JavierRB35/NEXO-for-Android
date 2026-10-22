package com.jruiz.nexo.connect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Preparación y validación de metadatos Nexo Connect v1.
 *
 * Siempre opera en memoria; NUNCA escribe en Descargas/MediaStore.
 * No cifra: el llamante DEBE cifrar y firmar el resultado antes de enviarlo.
 * Los documentos requieren además cifrar su archivo binario por separado.
 *
 * Prototipo aún no integrado en las pantallas de Nexo.
 */
public final class NexoConnectPayloadCodec {
    public static final String FORMAT = "nexo-connect";
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_METADATA_BYTES = 512 * 1024;
    public static final int MAX_LIST_ITEMS = 500;
    private static final int MAX_TEXT_CHARS = 240000;

    private static final String[] TYPES = {
        "appointment","note","list","journal","book","movie","document"
    };

    private NexoConnectPayloadCodec() {}

    /**
     * @param entity Uno de los siete tipos admitidos.
     * @param source Una ficha Nexo ya cargada desde SQLite en el dispositivo.
     * @param listEntries Entradas si entity=list, nulo en otro caso.
     */
    public static byte[] encode(String entity, JSONObject source, JSONArray listEntries)
            throws JSONException {
        JSONObject safe = sanitize(entity, source, listEntries);
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("entity", entity);
        root.put("data", safe);
        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_METADATA_BYTES)
            throw new IllegalArgumentException("Metadatos de intercambio demasiado grandes");
        return encoded;
    }

    /**
     * Acepta únicamente metadatos DESCIFRADOS; no usar con contenido de red en
     * texto claro ni tratar el éxito de parseo como prueba de autenticidad.
     * Devuelve campos permitidos, descartando ids, paths, URLs privadas o extras.
     */
    public static JSONObject decodeDecrypted(byte[] plain) throws JSONException {
        if (plain == null || plain.length == 0 || plain.length > MAX_METADATA_BYTES)
            throw new IllegalArgumentException("Contenido cifrado inválido");
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(plain)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Codificación UTF-8 incorrecta", e);
        }
        JSONObject root = new JSONObject(decoded);
        if (!FORMAT.equals(root.optString("format"))
                || root.optInt("version", -1) != FORMAT_VERSION)
            throw new IllegalArgumentException("Versión de intercambio incompatible");
        String entity = root.optString("entity", "");
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalArgumentException("Ficha no válida");
        byte[] normalized = encode(entity, data, data.optJSONArray("entries"));
        // Round-trip to apply the exact same allowlist in both directions.
        return new JSONObject(new String(normalized, StandardCharsets.UTF_8));
    }

    public static JSONObject sanitize(String entity, JSONObject input, JSONArray entries)
            throws JSONException {
        if (!allowedType(entity) || input == null)
            throw new IllegalArgumentException("Tipo de contenido no admitido");
        JSONObject out = new JSONObject();
        switch (entity) {
            case "appointment":
                copy(input,out, "type","title","body","originalText",
                        "category","subcategory","tags","dueAt","endAt",
                        "hasTime","allDay","locationText","mapsUrl",
                        "priority","favorite","status","needsReview",
                        "recurrence","recurrenceUntil","durationMinutes",
                        "voiceEnabled","lat","lon");
                copyLeadTimes(input,out);
                break;
            case "note":
                copy(input,out, "type","title","body","tags","category",
                        "originalText","favorite");
                break;
            case "list":
                copy(input,out, "title","kind","reusable","favorite");
                JSONArray list = entries == null ? input.optJSONArray("entries") : entries;
                if (list == null) list = new JSONArray();
                if (list.length() > MAX_LIST_ITEMS)
                    throw new IllegalArgumentException("Lista demasiado larga");
                JSONArray cleanList = new JSONArray();
                for (int i=0; i<list.length(); i++) {
                    JSONObject entry = list.optJSONObject(i);
                    if (entry == null)
                        throw new IllegalArgumentException("Elemento de lista inválido");
                    JSONObject clean = new JSONObject();
                    copy(entry,clean,"text","checked","sortOrder","category");
                    if (clean.optString("text","").trim().isEmpty())
                        throw new IllegalArgumentException("Elemento vacío");
                    cleanList.put(clean);
                }
                out.put("entries",cleanList);
                break;
            case "journal":
                copy(input,out, "kind","title","summary","body","tags","favorite");
                break;
            case "book":
                copy(input,out, "title","author","status","progress","startDate",
                        "finishDate","abandonedDate","summary","favorite","rating","category");
                if (out.optString("author","").trim().isEmpty())
                    throw new IllegalArgumentException("Falta autor");
                break;
            case "movie":
                copy(input,out, "title","creator","mediaType","status","progress",
                        "startDate","finishDate","abandonedDate","summary",
                        "favorite","rating","category");
                String mediaType = out.optString("mediaType","movie");
                if (!mediaType.equals("movie") && !mediaType.equals("series"))
                    throw new IllegalArgumentException("Tipo de Filmoteca no admitido");
                break;
            case "document":
                // Solo metadatos. El archivo original se transmite mediante
                // un sobre cifrado independiente, jamás usando la ruta local.
                copy(input,out, "title","description","category","tags",
                        "fileName","mimeType","attachmentSize","attachmentSha256");
                String fileName = out.optString("fileName","").trim();
                if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")
                        || fileName.contains("/") || fileName.contains("\\"))
                    throw new IllegalArgumentException("Nombre de documento inválido");
                long size = out.optLong("attachmentSize",-1);
                if (size < 0 || size > 10L * 1024L * 1024L)
                    throw new IllegalArgumentException("Documento fuera del límite");
                String sha = out.optString("attachmentSha256","");
                if (!sha.matches("[a-fA-F0-9]{64}"))
                    throw new IllegalArgumentException("Huella de archivo inválida");
                break;
            default:
                throw new IllegalArgumentException("Tipo no compatible");
        }
        if (out.optString("title","").trim().isEmpty()
                || out.optString("title","").length() > 250)
            throw new IllegalArgumentException("Título obligatorio o excesivo");
        return out;
    }

    private static boolean allowedType(String entity) {
        if (entity == null) return false;
        for (String type : TYPES) if (type.equals(entity)) return true;
        return false;
    }

    private static void copyLeadTimes(JSONObject input, JSONObject dest)
            throws JSONException {
        JSONArray times = input.optJSONArray("reminderLeadMinutes");
        if (times == null) return;
        if (times.length()>12) throw new IllegalArgumentException("Demasiados avisos");
        JSONArray clean = new JSONArray();
        for (int i=0;i<times.length();i++) {
            Object raw=times.opt(i);
            if (!(raw instanceof Number))
                throw new IllegalArgumentException("Aviso inválido");
            long minutes=((Number)raw).longValue();
            if (minutes<0 || minutes>525600)
                throw new IllegalArgumentException("Aviso fuera del límite");
            clean.put(minutes);
        }
        dest.put("reminderLeadMinutes",clean);
    }

    private static void copy(JSONObject input, JSONObject output, String... names)
            throws JSONException {
        for (String name : names) {
            if (!input.has(name) || input.isNull(name)) continue;
            Object value = input.get(name);
            // JSON structure, arrays, null and arbitrary Java objects cannot
            // be copied to a scalar field except the allowlisted lead times.
            if (!(value instanceof String || value instanceof Number ||
                    value instanceof Boolean))
                throw new IllegalArgumentException("Campo no escalar: " + name);
            if (value instanceof String && ((String)value).length() > MAX_TEXT_CHARS)
                throw new IllegalArgumentException("Texto demasiado largo");
            output.put(name,value);
        }
    }
}
