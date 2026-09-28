import android.content.Context;
import com.jruiz.nexo.core.*;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;
import org.json.*;

/** Production place fields, URL construction and service, with DB/scheduler substitutes. */
public final class PlaceServiceSelfTest {
    private static int passed;
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void test(String name,Runnable fn){ReminderScheduler.reset();fn.run();passed++;System.out.println("OK "+name);}
    private static JSONObject draft(){return new JSONObject().put("title","Mirador del Garbí").put("body","Vistas y paseo corto").put("locationText","39.696,-0.392").put("label","Lugar bonito");}
    public static void main(String[] args){
        test("Maps short and full links remain links, not navigation query strings",()->{
            for(String url:new String[]{"https://maps.app.goo.gl/Example","https://goo.gl/maps/OldLink","https://www.google.com/maps/place/Valencia/","https://maps.google.es/?q=Valencia","https://www.google.com/maps/search/?api=1&query=Valencia%2C+Spain"})check(url.equals(PlaceEntry.mapsUrl(url)),url);
            check(PlaceEntry.mapsUrl("maps.app.goo.gl/Example").equals("https://maps.app.goo.gl/Example"),"Missing scheme");
        });
        test("Addresses and coordinates are encoded as Google Maps searches",()->{
            check(PlaceEntry.mapsUrl("Calle del Café 8, València").equals("https://www.google.com/maps/search/?api=1&query=Calle+del+Caf%C3%A9+8%2C+Val%C3%A8ncia"),"UTF-8 address");
            check(PlaceEntry.mapsUrl("39.4699,-0.3763").endsWith("query=39.4699%2C-0.3763"),"Coordinates");
            check(PlaceEntry.mapsUrl("Café & Bar #2").endsWith("query=Caf%C3%A9+%26+Bar+%232"),"Query injection");
        });
        test("Unrelated links and executable schemes are rejected",()->{
            for(String bad:new String[]{"javascript:alert(1)","intent://maps", "file:///tmp/map", "https://example.com/maps/a", "https://www.google.com.evil.test/maps/a", "https://www.google.com/search?q=x", "https://user@maps.app.goo.gl/a", "https://goo.gl/not-maps", "//maps.app.goo.gl/a", "https://maps.app.goo.gl:81/a"}){
                boolean rejected=false;try{PlaceEntry.mapsUrl(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,bad);
            }
        });
        test("Empty and overlong map requests report a usable error",()->{
            for(String bad:new String[]{"", "a".repeat(2049)}){boolean rejected=false;try{PlaceEntry.mapsUrl(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Bad length");}
        });
        test("Place titles containing dates never create appointments or alarms",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);
            JSONObject p=draft().put("title","Cena mañana a las 20 y avísame").put("type","event").put("dueAt",System.currentTimeMillis()+86400000L).put("hasTime",true).put("recurrence","weekly").put("voiceEnabled",true).put("reminderLeadMinutes",new JSONArray().put(60));
            JSONObject out=service.savePlace(p),i=db.getItem(out.optLong("id"));check(out.optBoolean("ok"),"Save failed");check("place".equals(i.optString("type"))&&"Lugares".equals(i.optString("category")),"Not a place");check(i.optLong("dueAt")==0&&"none".equals(i.optString("recurrence"))&&!i.optBoolean("voiceEnabled"),"Calendar data leaked");check(ReminderScheduler.scheduled.isEmpty()&&db.reminders.isEmpty(),"Alarm created");
        });
        test("Place label and complete description are saved as supplied",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);
            for(String label:new String[]{"Quiero ir","He estado","Restaurante","Lugar bonito","Etiqueta antigua"}){
                JSONObject out=service.savePlace(draft().put("label",label).put("body","Descripción completa\nSegunda línea y detalle final."));JSONObject i=db.getItem(out.optLong("id"));check(label.equals(i.optString("subcategory")),"Label lost");check(i.optString("body").endsWith("detalle final."),"Description shortened");
            }
        });
        test("Updating an old restaurant preserves id, attachments and unrelated metadata",()->{
            DatabaseHelper db=new DatabaseHelper();long id=db.insertItem(new JSONObject().put("type","restaurant").put("title","Antiguo").put("body","Texto anterior").put("dueAt",999999).put("recurrence","weekly").put("attachmentPath","stored/photo.jpg").put("originalText","Texto importado").put("tags","terraza").put("createdAt",123));
            NexoService service=new NexoService(new Context(),db);JSONObject out=service.savePlace(draft().put("id",id));JSONObject i=db.getItem(id);check(out.optBoolean("ok")&&out.optLong("id")==id&&db.items.size()==1,"Identity changed");check("stored/photo.jpg".equals(i.optString("attachmentPath"))&&"Texto importado".equals(i.optString("originalText"))&&i.optLong("createdAt")==123,"Metadata lost");check(i.optLong("dueAt")==0&&"none".equals(i.optString("recurrence")),"Still a dated record");
        });
        test("Cleared location and description stay empty after editing",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);long id=service.savePlace(draft()).optLong("id");service.savePlace(draft().put("id",id).put("body","").put("locationText",""));JSONObject i=db.getItem(id);check(i.optString("body").isEmpty()&&i.optString("locationText").isEmpty(),"Cleared fields restored");
        });
        test("Restaurant and place searches include both legacy and labelled cards",()->{
            JSONObject old=new JSONObject().put("type","restaurant").put("category","Restaurantes"),current=new JSONObject().put("type","place").put("category","Lugares").put("subcategory","Restaurante"),landscape=new JSONObject().put("type","place").put("subcategory","Lugar bonito");
            for(JSONObject i:new JSONObject[]{old,current}){check(PlaceEntry.matchesSearchCategory(i,"restaurantes"),"Restaurant missing");check(PlaceEntry.matchesSearchCategory(i,"lugares"),"Place missing");}check(!PlaceEntry.matchesSearchCategory(landscape,"restaurantes"),"Landscape treated as restaurant");check(!PlaceEntry.matchesSearchCategory(new JSONObject().put("type","event"),"lugares"),"Cita treated as place");
        });
        test("Blank titles and invalid map links do not create or modify records",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);check(!service.savePlace(draft().put("title"," ")).optBoolean("ok"),"Blank title accepted");check(!service.savePlace(draft().put("locationText","https://example.com")).optBoolean("ok"),"Wrong link accepted");check(db.items.isEmpty(),"Bad save wrote data");
            long id=service.savePlace(draft()).optLong("id");String before=db.getItem(id).toString();check(!service.savePlace(draft().put("id",id).put("title","")).optBoolean("ok"),"Bad edit accepted");check(before.equals(db.getItem(id).toString()),"Failed edit changed data");
        });
        test("The place editor cannot overwrite a Cita or a missing record",()->{
            DatabaseHelper db=new DatabaseHelper();long id=db.insertItem(new JSONObject().put("type","event").put("title","Cita"));NexoService service=new NexoService(new Context(),db);check(!service.savePlace(draft().put("id",id)).optBoolean("ok"),"Cita overwritten");check(!service.savePlace(draft().put("id",999)).optBoolean("ok"),"Missing place accepted");check("Cita".equals(db.getItem(id).optString("title")),"Cita changed");
        });
        test("Old place alarms are cancelled while Cita alarms and all rules remain",()->{
            DatabaseHelper db=new DatabaseHelper();long place=db.insertItem(new JSONObject().put("type","restaurant").put("dueAt",9876).put("title","Lugar")),appointment=db.insertItem(new JSONObject().put("type","event").put("title","Cita"));long rp=db.insertReminderMinutes(place,"Lugar",9999999,60,9876),ra=db.insertReminderMinutes(appointment,"Cita",9999999,0,9999999);
            new NexoService(new Context(),db);check(ReminderScheduler.cancelled.contains(rp)&&!ReminderScheduler.cancelled.contains(ra),"Wrong alarm cancelled");check(db.getActiveRemindersForItem(place).length()==0&&db.getActiveRemindersForItem(appointment).length()==1,"Wrong rules disabled");check(db.reminders.size()==2&&db.getItem(place).optLong("dueAt")==9876,"Legacy data deleted on opening");
        });
        test("Editing a place also silences an old alarm introduced during the session",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);long id=db.insertItem(new JSONObject().put("type","place").put("title","Importado"));long alarm=db.insertReminderMinutes(id,"Importado",9999999,0,9999999);service.savePlace(draft().put("id",id));check(ReminderScheduler.cancelled.contains(alarm)&&db.getActiveRemindersForItem(id).length()==0&&db.reminders.size()==1,"Old alarm retained or deleted");
        });
        System.out.println("Places native: "+passed+" scenarios passed");
    }
}
