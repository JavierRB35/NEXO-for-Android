package com.jruiz.nexo.connectui;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jruiz.nexo.connect.NexoConnectPayloadCodec;
import com.jruiz.nexo.data.BookRepository;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.data.MediaRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lab-only, read-only bridge from existing Nexo records to E2EE metadata.
 * All type names and SQL fragments are fixed strings, never caller-provided.
 * No plaintext data is placed in Intents, logs, Downloads or MediaStore.
 *
 * Critical: explicit failure beats quietly dropping recurrence, alarms,
 * attachments, participants or other fields with consequences on import.
 * This class creates a DRAFT, not an authorization to transmit it.
 */
public final class NexoConnectHostShare {
    private NexoConnectHostShare() {}

    public static final String ITEMS="items", LISTS="lists", JOURNAL="journal",
            BOOKS="books", MOVIES="movies";

    public static final class Entry {
        public final long id;
        public final String title,kind;
        private Entry(long id,String title,String kind) {
            this.id=id;this.title=title;this.kind=kind;
        }
    }

    public static final class Draft {
        public final String entity,displayKind,title;
        public final JSONObject safeData;
        private Draft(String entity,String displayKind,JSONObject safeData) {
            this.entity=entity;
            this.displayKind=displayKind;
            this.safeData=safeData;
            this.title=safeData.optString("title","");
        }
    }

    public static String[] sections() {
        return new String[]{"Notas y citas","Listas","Cuaderno y frases",
                "Libros","Películas y series"};
    }

    public static String[] sectionKinds() {
        return new String[]{ITEMS,LISTS,JOURNAL,BOOKS,MOVIES};
    }

    /** Summaries only; no note body or document path leaves SQLite. */
    public static List<Entry> list(DatabaseHelper helper,String kind) throws Exception {
        if(helper==null)throw new IllegalArgumentException("Missing Nexo database");
        final String sql;
        switch(kind) {
            case ITEMS:
                sql="SELECT id,title,type FROM items WHERE deleted_at=0 "+
                    "AND type IN ('note','event','task','reminder','expiry',"+
                    "'place','restaurant','document') ORDER BY updated_at DESC LIMIT 100";
                break;
            case LISTS:
                sql="SELECT id,title,'list' FROM lists ORDER BY updated_at DESC LIMIT 100";
                break;
            case JOURNAL:
                sql="SELECT id,title,kind FROM journal_entries "+
                    "ORDER BY updated_at DESC LIMIT 100";
                break;
            case BOOKS:
                sql="SELECT id,title,'book' FROM books ORDER BY updated_at DESC LIMIT 100";
                break;
            case MOVIES:
                sql="SELECT id,title,media_type FROM media_items "+
                    "ORDER BY updated_at DESC LIMIT 100";
                break;
            default:throw new IllegalArgumentException("Unknown Nexo section");
        }
        ArrayList<Entry> result=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)) {
            while(c.moveToNext()){
                long id=c.getLong(0);
                if(id<=0)continue;
                String title=c.getString(1);
                if(title==null||title.trim().isEmpty())title="Sin título";
                String type=c.getString(2);
                result.add(new Entry(id,title,type==null?"":type));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * The final read happens after selection, so deleted or edited rows do
     * not get sent from a stale list result.
     */
    public static Draft prepare(DatabaseHelper helper,String section,long id)
            throws Exception {
        if(helper==null||id<=0)throw new IllegalArgumentException("Invalid selection");
        JSONObject source;
        String entity,display;
        switch(section) {
            case ITEMS:
                source=helper.getItem(id);
                String type=source.optString("type","");
                if("note".equals(type)) {
                    entity="note";display="Nota";
                } else if("event".equals(type)||"task".equals(type)||
                          "reminder".equals(type)||"expiry".equals(type)) {
                    entity="appointment";display="Cita o evento";
                    // The native importer does not yet schedule recurrence,
                    // reminders, participants or timezone-specific occurrences.
                    if(!"none".equals(source.optString("recurrence","none"))||
                            !source.optString("recurrenceRule","").isEmpty()||
                            !source.optString("participantsText","").isEmpty()||
                            source.optLong("recurrenceUntil",0)>0||
                            hasArray(source,"reminderLeadMinutes"))
                        throw new UnsupportedOperationException(
                            "Esta cita tiene avisos, asistentes o recurrencia; "+
                            "su importación segura está pendiente.");
                    if(helper.getActiveRemindersForItem(id).length()>0)
                        throw new UnsupportedOperationException(
                            "Esta cita tiene alarmas. No se enviará hasta "+
                            "poder conservarlas de forma segura.");
                } else if("document".equals(type)) {
                    throw new UnsupportedOperationException(
                        "Los documentos requieren cifrar el archivo original. "+
                        "Aún no está disponible este tipo de envío.");
                } else if("place".equals(type)||"restaurant".equals(type)) {
                    throw new UnsupportedOperationException(
                        "Compartir lugares con todos sus campos está en desarrollo.");
                } else {
                    throw new UnsupportedOperationException(
                        "Esta ficha todavía no tiene un importador seguro.");
                }
                if(!source.optString("attachmentPath","").isEmpty())
                    throw new UnsupportedOperationException(
                        "La ficha contiene un adjunto que requiere cifrado de archivo.");
                break;
            case LISTS:
                source=helper.getList(id);
                if(source.length()==0)throw new IllegalArgumentException("Lista no encontrada");
                source.put("entries",helper.getListEntries(id));
                entity="list";display="Lista";
                break;
            case JOURNAL:
                source=helper.getJournalEntry(id);
                if(source.length()==0)throw new IllegalArgumentException("Entrada no encontrada");
                entity="journal";
                display="quote".equals(source.optString("kind"))?"Frase":"Cuaderno";
                break;
            case BOOKS:
                source=BookRepository.get(helper,id);
                entity="book";display="Libro";break;
            case MOVIES:
                source=MediaRepository.get(helper,id);
                entity="movie";display="Película o serie";break;
            default:
                throw new IllegalArgumentException("Sección no disponible");
        }
        if(source.length()==0||source.optString("title","").trim().isEmpty())
            throw new IllegalArgumentException("La ficha no está disponible");
        // Apply the exact same allowlist as the signed E2EE sender.
        JSONObject safe=NexoConnectPayloadCodec.sanitize(entity,source,null);
        byte[] encoded=NexoConnectPayloadCodec.encode(entity,safe,null);
        if(encoded.length==0||encoded.length>NexoConnectPayloadCodec.MAX_METADATA_BYTES)
            throw new SecurityException("Ficha demasiado grande");
        return new Draft(entity,display,safe);
    }

    private static boolean hasArray(JSONObject value,String key) {
        JSONArray list=value.optJSONArray(key);
        return list!=null&&list.length()>0;
    }
}
