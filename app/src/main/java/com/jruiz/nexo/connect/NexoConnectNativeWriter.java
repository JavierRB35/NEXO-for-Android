package com.jruiz.nexo.connect;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * EXPERIMENTAL native SQLite writer for a previously verified Nexo Connect
 * payload. Used exclusively as EntityWriter inside NexoConnectAtomicImporter.
 *
 * This inserts fresh IDs into existing Nexo tables with the same SQLiteDatabase
 * supplied by the import ledger. It never updates local records, never opens
 * another SQLite connection, never starts a transaction and never writes files.
 *
 * Not wired to the production UI. The outer caller MUST verify HPKE/Ed25519,
 * trusted peer fingerprints, recipient, generation and expiry before import.
 */
public final class NexoConnectNativeWriter implements NexoConnectAtomicImporter.EntityWriter {
    public static final NexoConnectNativeWriter INSTANCE = new NexoConnectNativeWriter();
    private static final String SOURCE = "nexo-connect";
    private NexoConnectNativeWriter() {}

    @Override public long insertNew(SQLiteDatabase db, String entity, JSONObject data) throws Exception {
        if (db == null || data == null || !db.inTransaction())
            throw new IllegalStateException("Nexo Connect import needs its ledger transaction");
        switch (entity) {
            case "note": return note(db, data);
            case "appointment": return appointment(db, data);
            case "list": return list(db, data);
            case "journal": return journal(db, data);
            case "book": return book(db, data);
            case "movie": return movie(db, data);
            case "document":
                // Metadata alone is NOT a document. Keep it pending until
                // authenticated, encrypted attachment transfer is implemented.
                throw new UnsupportedOperationException("Document attachment required");
            default: throw new IllegalArgumentException("Unsupported Nexo Connect entity");
        }
    }

    private static long insert(SQLiteDatabase db, String table, ContentValues fields) {
        long result = db.insertOrThrow(table, null, fields);
        if (result <= 0) throw new IllegalStateException("Native insert failed");
        return result;
    }

    private static ContentValues named(JSONObject data) {
        ContentValues values = new ContentValues();
        String title = text(data,"title").trim();
        if (title.isEmpty() || title.length() > 250)
            throw new IllegalArgumentException("Missing or oversize title");
        values.put("title",title);
        long now = System.currentTimeMillis();
        values.put("created_at",now);
        values.put("updated_at",now);
        return values;
    }

    private static String text(JSONObject d,String key) {
        return d.optString(key,"");
    }

    private static boolean flag(JSONObject d,String key) {
        return d.optBoolean(key,false);
    }

    private static int boundedInt(JSONObject d,String key,int fallback,int min,int max) {
        Object raw=d.opt(key);
        if(raw==null || raw==JSONObject.NULL) return fallback;
        if(!(raw instanceof Number) || ((Number)raw).doubleValue() != ((Number)raw).longValue())
            throw new IllegalArgumentException("Invalid integer: "+key);
        long value=((Number)raw).longValue();
        if(value<min || value>max)throw new IllegalArgumentException("Out-of-range integer: "+key);
        return (int)value;
    }

    private static long nonnegativeTime(JSONObject d,String key) {
        Object raw=d.opt(key);
        if(raw==null || raw==JSONObject.NULL) return 0;
        if(!(raw instanceof Number) || ((Number)raw).doubleValue()!=((Number)raw).longValue())
            throw new IllegalArgumentException("Invalid timestamp: "+key);
        long value=((Number)raw).longValue();
        if(value<0 || value>253402300799999L)
            throw new IllegalArgumentException("Invalid timestamp: "+key);
        return value;
    }

    private static String date(JSONObject data,String name) {
        String value=text(data,name).trim();
        if(!value.isEmpty() && !LocalDate.parse(value).toString().equals(value))
            throw new IllegalArgumentException("Invalid date: "+name);
        return value;
    }

    private static boolean oneOf(String value,String... options) {
        for(String option:options) if(option.equals(value))return true;
        return false;
    }

    private static void max(String str,int n,String what) {
        if(str.length()>n) throw new IllegalArgumentException("Too long: "+what);
    }

    private static void optional(ContentValues values, JSONObject data, String name, String column, int max) {
        String value=text(data,name); max(value,max,name); values.put(column,value);
    }

    private static long note(SQLiteDatabase db,JSONObject data) {
        ContentValues v=named(data);
        String body=text(data,"body");
        max(body,240000,"note body");
        v.put("type","note");
        v.put("body",body);
        optional(v,data,"originalText","original_text",240000);
        optional(v,data,"tags","tags",10000);
        String category=text(data,"category").trim();
        max(category,120,"note category");
        v.put("category",category.isEmpty()?"Notas":category);
        v.put("favorite",flag(data,"favorite")?1:0);
        v.put("source",SOURCE);
        v.put("note_title_manual",1);
        // Other required fields use safe defaults defined in DatabaseHelper.
        return insert(db,"items",v);
    }

    private static long appointment(SQLiteDatabase db,JSONObject data) {
        String type=text(data,"type");
        if(!oneOf(type,"event","task","reminder","expiry"))
            throw new IllegalArgumentException("Unsupported appointment type");
        JSONArray lead=data.optJSONArray("reminderLeadMinutes");
        if(lead!=null && lead.length()>0)
            throw new UnsupportedOperationException("Reminder scheduler not yet integrated");
        String recurrence=text(data,"recurrence");
        if(!recurrence.isEmpty() && !"none".equals(recurrence))
            throw new UnsupportedOperationException("Recurring appointment not yet integrated");
        long due=nonnegativeTime(data,"dueAt");
        if(due<=0)throw new IllegalArgumentException("Appointment needs a date");
        long end=nonnegativeTime(data,"endAt");
        if(end!=0 && end<due)throw new IllegalArgumentException("End before start");
        if(nonnegativeTime(data,"recurrenceUntil")>0)
            throw new UnsupportedOperationException("Recurrence metadata not yet supported");
        ContentValues v=named(data);
        v.put("type",type);
        v.put("body",text(data,"body"));
        optional(v,data,"originalText","original_text",240000);
        optional(v,data,"category","category",120);
        optional(v,data,"subcategory","subcategory",120);
        optional(v,data,"tags","tags",10000);
        optional(v,data,"locationText","location_text",4000);
        String maps=text(data,"mapsUrl");
        if(!maps.isEmpty() && !maps.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Maps URL");
        max(maps,2000,"maps URL");
        v.put("maps_url",maps);
        v.put("due_at",due);
        v.put("end_at",end==0?due:end);
        v.put("has_time",flag(data,"hasTime")?1:0);
        v.put("all_day",flag(data,"allDay")?1:0);
        v.put("priority",boundedInt(data,"priority",1,1,3));
        v.put("duration_minutes",boundedInt(data,"durationMinutes",0,0,525600));
        v.put("favorite",flag(data,"favorite")?1:0);
        v.put("needs_review",flag(data,"needsReview")?1:0);
        // Preserve the basic status without importing someone else's completion
        // tracking or an outdated alarm; follow normal Nexo status defaults.
        v.put("status","task".equals(type)?"pending":"active");
        v.put("status_manual",0);
        v.put("voice_enabled",0);
        v.put("recurrence","none");
        v.put("recurrence_until",0);
        v.put("source",SOURCE);
        if(data.has("lat") && !data.isNull("lat")){
            double lat=data.optDouble("lat",Double.NaN);
            if(!Double.isFinite(lat) || lat < -90 || lat > 90)
                throw new IllegalArgumentException("Invalid latitude");
            v.put("lat",lat);
        }
        if(data.has("lon") && !data.isNull("lon")){
            double lon=data.optDouble("lon",Double.NaN);
            if(!Double.isFinite(lon) || lon < -180 || lon > 180)
                throw new IllegalArgumentException("Invalid longitude");
            v.put("lon",lon);
        }
        return insert(db,"items",v);
    }

    private static long list(SQLiteDatabase db,JSONObject data) {
        ContentValues v=named(data);
        String kind=text(data,"kind");
        max(kind,120,"list kind");
        v.put("kind",kind.isEmpty()?"General":kind);
        v.put("reusable",flag(data,"reusable")?1:0);
        v.put("favorite",flag(data,"favorite")?1:0);
        JSONArray entries=data.optJSONArray("entries");
        if(entries==null)entries=new JSONArray();
        if(entries.length()>500)throw new IllegalArgumentException("Too many list entries");
        // Validate every child first; a later failure still rolls back the parent.
        for(int i=0;i<entries.length();i++){
            JSONObject row=entries.optJSONObject(i);
            if(row==null||text(row,"text").trim().isEmpty())
                throw new IllegalArgumentException("Empty list entry");
            max(text(row,"text"),4000,"list entry");
            max(text(row,"category"),120,"list entry category");
            boundedInt(row,"sortOrder",i,0,1000000);
        }
        long id=insert(db,"lists",v);
        long now=System.currentTimeMillis();
        for(int i=0;i<entries.length();i++){
            JSONObject row=entries.getJSONObject(i);
            ContentValues item=new ContentValues();
            item.put("list_id",id);
            item.put("text",text(row,"text").trim());
            item.put("checked",flag(row,"checked")?1:0);
            item.put("sort_order",boundedInt(row,"sortOrder",i,0,1000000));
            item.put("category",text(row,"category"));
            item.put("created_at",now);
            insert(db,"list_entries",item);
        }
        return id;
    }

    private static long journal(SQLiteDatabase db,JSONObject data) {
        ContentValues v=named(data);
        String kind=text(data,"kind");
        if(!kind.isEmpty() && !oneOf(kind,"thought","quote"))
            throw new IllegalArgumentException("Invalid journal kind");
        String body=text(data,"body").trim();
        if(body.isEmpty())throw new IllegalArgumentException("Journal entry needs a body");
        max(body,240000,"journal body");
        v.put("kind",kind.isEmpty()?"thought":kind);
        v.put("body",body);
        optional(v,data,"summary","summary",20000);
        optional(v,data,"tags","tags",10000);
        v.put("favorite",flag(data,"favorite")?1:0);
        v.put("source",SOURCE);
        return insert(db,"journal_entries",v);
    }

    private static ContentValues mediaBase(JSONObject data) {
        ContentValues v=named(data);
        String status=text(data,"status");
        if(status.isEmpty()) status="want";
        if(!oneOf(status,"want","reading","finished","abandoned"))
            throw new IllegalArgumentException("Invalid media status");
        int progress=boundedInt(data,"progress",0,0,100);
        int rating=boundedInt(data,"rating",0,0,5);
        String start=date(data,"startDate");
        String finish=date(data,"finishDate");
        String abandoned=date(data,"abandonedDate");
        if(!start.isEmpty() && !finish.isEmpty() && start.compareTo(finish)>0)
            throw new IllegalArgumentException("Invalid finish date");
        if(!start.isEmpty() && !abandoned.isEmpty() && start.compareTo(abandoned)>0)
            throw new IllegalArgumentException("Invalid abandon date");
        // Preserve received status, progress and dates exactly. Never
        // silently synthesize today's date from a received finished status.
        v.put("status",status);
        v.put("progress",progress);
        v.put("rating",rating);
        v.put("start_date",start);
        v.put("finish_date",finish);
        v.put("abandoned_date",abandoned);
        optional(v,data,"summary","summary",20000);
        optional(v,data,"category","category",80);
        v.put("favorite",flag(data,"favorite")?1:0);
        return v;
    }

    private static long book(SQLiteDatabase db,JSONObject data) {
        ContentValues v=mediaBase(data);
        String author=text(data,"author").trim();
        if(author.isEmpty())throw new IllegalArgumentException("Missing author");
        max(author,180,"author");
        v.put("author",author);
        return insert(db,"books",v);
    }

    private static long movie(SQLiteDatabase db,JSONObject data) {
        ContentValues v=mediaBase(data);
        String type=text(data,"mediaType");
        if(type.isEmpty())type="movie";
        if(!oneOf(type,"movie","series"))
            throw new IllegalArgumentException("Invalid film library type");
        v.put("media_type",type);
        optional(v,data,"creator","creator",180);
        return insert(db,"media_items",v);
    }
}
