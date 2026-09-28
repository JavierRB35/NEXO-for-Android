package com.jruiz.nexo.exchange;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/** Intercambio local Nexo↔Nexo mediante un archivo JSON versionado con extensión .nexoshare. */
public final class NexoExchangeManager {
    public static final String MIME = "application/x-nexo-share";
    public static final int FORMAT_VERSION = 1;
    private static final int MAX_IMPORT_BYTES = 512 * 1024;

    private NexoExchangeManager() {}

    public static Uri exportItem(Context context, JSONObject item, JSONArray reminders) throws Exception {
        JSONObject data = new JSONObject();
        copy(item, data, "type","title","body","originalText","category","subcategory","tags","dueAt","hasTime","locationText","priority","favorite","status","needsReview","recurrence","recurrenceUntil","durationMinutes","voiceEnabled");
        if (item.has("lat") && !item.isNull("lat")) data.put("lat", item.optDouble("lat"));
        if (item.has("lon") && !item.isNull("lon")) data.put("lon", item.optDouble("lon"));
        data.put("source", "nexo-share");

        JSONArray leads = new JSONArray();
        if (reminders != null) {
            for (int i=0;i<reminders.length();i++) {
                JSONObject r=reminders.optJSONObject(i); if(r==null || !r.optBoolean("enabled",true)) continue;
                leads.put(Math.max(0,r.optInt("leadMinutes",Math.max(0,r.optInt("leadDays",0))*1440)));
            }
        }
        data.put("reminderLeadMinutes", leads);
        return write(context, envelope("item", data), item.optString("title","elemento"));
    }

    public static Uri exportList(Context context, JSONObject list, JSONArray entries) throws Exception {
        JSONObject data = new JSONObject();
        data.put("title", list.optString("title","Lista"));
        data.put("kind", list.optString("kind","General"));
        data.put("reusable", list.optBoolean("reusable",false));
        data.put("favorite", list.optBoolean("favorite",false));
        JSONArray safeEntries = new JSONArray();
        if (entries != null) for (int i=0;i<entries.length();i++) {
            JSONObject e=entries.optJSONObject(i); if(e==null)continue;
            JSONObject x=new JSONObject();
            x.put("text",e.optString("text",""));
            x.put("checked",e.optBoolean("checked",false));
            x.put("sortOrder",Math.max(0,e.optInt("sortOrder",i)));
            x.put("category",e.optString("category",""));
            safeEntries.put(x);
        }
        data.put("entries",safeEntries);
        return write(context, envelope("list", data), list.optString("title","lista"));
    }

    public static Uri exportJournal(Context context, JSONObject journal) throws Exception {
        JSONObject data=new JSONObject();
        copy(journal,data,"kind","title","summary","body","tags","favorite");
        data.put("source","nexo-share");
        return write(context,envelope("journal",data),journal.optString("title","cuaderno"));
    }

    public static JSONObject read(Context context, Uri uri) throws Exception {
        byte[] bytes=readBytes(context,uri,MAX_IMPORT_BYTES);
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!"nexo-share".equals(root.optString("format"))) throw new IllegalArgumentException("No es un archivo de intercambio de Nexo");
        int version=root.optInt("version",0);
        if(version<1 || version>FORMAT_VERSION) throw new IllegalArgumentException("Versión de archivo Nexo no compatible");
        String entity=root.optString("entity","");
        if(!entity.equals("item")&&!entity.equals("list")&&!entity.equals("journal")) throw new IllegalArgumentException("Tipo de contenido Nexo no compatible");
        JSONObject data=root.optJSONObject("data");
        if(data==null) throw new IllegalArgumentException("Archivo Nexo incompleto");
        return root;
    }

    public static JSONObject preview(JSONObject root) {
        JSONObject out=new JSONObject();
        try {
            String entity=root.optString("entity",""); JSONObject d=root.optJSONObject("data"); if(d==null)d=new JSONObject();
            out.put("format","nexo"); out.put("entity",entity); out.put("version",root.optInt("version",0));
            out.put("title",d.optString("title", entity.equals("list")?"Lista":entity.equals("journal")?"Cuaderno":"Elemento"));
            out.put("body",d.optString("body",d.optString("summary","")));
            out.put("category",d.optString("category",d.optString("kind","")));
            out.put("dueAt",d.optLong("dueAt",0)); out.put("hasTime",d.optBoolean("hasTime",false));
            out.put("locationText",d.optString("locationText",""));
            if(entity.equals("list")) out.put("count",d.optJSONArray("entries")!=null?d.optJSONArray("entries").length():0);
        } catch(Exception ignored) {}
        return out;
    }

    public static byte[] readBytes(Context context, Uri uri, int maxBytes) throws Exception {
        try(InputStream in=context.getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null) throw new IllegalArgumentException("No se puede abrir el archivo");
            byte[] buf=new byte[8192]; int n,total=0;
            while((n=in.read(buf))>0){total+=n;if(total>maxBytes)throw new IllegalArgumentException("El archivo es demasiado grande");out.write(buf,0,n);}return out.toByteArray();
        }
    }

    private static JSONObject envelope(String entity, JSONObject data) throws Exception {
        JSONObject root=new JSONObject(); root.put("format","nexo-share");root.put("version",FORMAT_VERSION);root.put("entity",entity);root.put("createdAt",System.currentTimeMillis());root.put("data",data);return root;
    }

    private static Uri write(Context context, JSONObject root, String title) throws Exception {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) throw new UnsupportedOperationException("Compartir como Nexo requiere Android 10 o superior");
        String fileName=sanitize(title)+"_"+System.currentTimeMillis()+".nexoshare";
        ContentResolver resolver=context.getContentResolver(); ContentValues v=new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);v.put(MediaStore.MediaColumns.MIME_TYPE,MIME);v.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/Nexo/Compartidos");v.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v); if(uri==null)throw new IllegalStateException("No se pudo crear el archivo Nexo");
        boolean ok=false;try(OutputStream out=resolver.openOutputStream(uri,"w")){if(out==null)throw new IllegalStateException("No se pudo escribir el archivo Nexo");out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();ok=true;}finally{if(!ok)try{resolver.delete(uri,null,null);}catch(Exception ignored){}}
        ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);resolver.update(uri,ready,null,null);return uri;
    }

    private static void copy(JSONObject src, JSONObject dst, String... keys) throws Exception { for(String k:keys)if(src.has(k)&&!src.isNull(k))dst.put(k,src.get(k)); }
    private static String sanitize(String title){String raw=title==null?"nexo":title.trim();String normalized=Normalizer.normalize(raw,Normalizer.Form.NFD).replaceAll("\\p{M}+","");String safe=normalized.replaceAll("[^a-zA-Z0-9._ -]","_").replaceAll("\\s+","_");if(safe.isBlank())safe="nexo";return safe.length()>50?safe.substring(0,50):safe;}
}
