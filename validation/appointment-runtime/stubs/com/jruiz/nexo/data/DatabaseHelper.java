package com.jruiz.nexo.data;
import java.util.*;
import org.json.*;
/** In-memory domain fixture. This is not a SQLite or migration test. */
public class DatabaseHelper {
    public final Map<Long,JSONObject> items=new LinkedHashMap<>();
    public final Map<Long,JSONObject> reminders=new LinkedHashMap<>();
    public String learned="";
    public int reschedules=0;
    private long itemSequence=1,reminderSequence=100;
    private JSONObject copy(JSONObject in) { JSONObject out=new JSONObject();in.keys().forEachRemaining(k->out.put(k,in.get(k)));return out; }
    public JSONArray getDeletedItems() { return new JSONArray(); }
    public JSONArray getActiveArchiveReminders() { JSONArray out=new JSONArray();reminders.values().stream().filter(r->r.optBoolean("enabled")&&(com.jruiz.nexo.core.PlaceEntry.isPlace(getItem(r.optLong("itemId")).optString("type"))||"document".equals(getItem(r.optLong("itemId")).optString("type")))).forEach(r->out.put(copy(r)));return out; }
    public boolean isAttachmentReferenced(String path) { return items.values().stream().anyMatch(i->path.equals(i.optString("attachmentPath"))); }
    public boolean failUpdate=false;
    public String bestLearnedCategory(String text) { return learned; }
    public long insertItem(JSONObject in) { long id=itemSequence++;items.put(id,copy(in).put("id",id));return id; }
    public JSONObject getItem(long id) { return copy(items.getOrDefault(id,new JSONObject())); }
    public boolean updateItem(JSONObject in) { if(failUpdate)return false; JSONObject current=items.get(in.optLong("id"));if(current==null)return false;in.keys().forEachRemaining(k->current.put(k,in.get(k)));return true; }
    public JSONObject getOccurrence(long id,long at) { return getItem(id); }
    public long findNextOccurrence(long id,long after) { JSONObject i=getItem(id);return !"done".equals(i.optString("status"))&&i.optLong("dueAt")>after?i.optLong("dueAt"):0; }
    public long insertReminderMinutes(long itemId,String title,long triggerAt,int lead,long at) { long id=reminderSequence++;reminders.put(id,new JSONObject().put("id",id).put("itemId",itemId).put("title",title).put("triggerAt",triggerAt).put("leadMinutes",lead).put("occurrenceAt",at).put("enabled",true));return id; }
    public JSONArray getReminderRulesForItem(long itemId) { JSONArray out=new JSONArray();reminders.values().stream().filter(r->r.optLong("itemId")==itemId).forEach(r->out.put(copy(r)));return out; }
    public JSONArray getActiveRemindersForItem(long itemId) { JSONArray out=new JSONArray();reminders.values().stream().filter(r->r.optLong("itemId")==itemId&&r.optBoolean("enabled")).forEach(r->out.put(copy(r)));return out; }
    public boolean setReminderEnabled(long id,boolean enabled) { JSONObject r=reminders.get(id);if(r==null)return false;r.put("enabled",enabled);return true; }
    public void deleteRemindersForItem(long id) { reminders.values().removeIf(r->r.optLong("itemId")==id); }
    public JSONArray rescheduleRemindersForItem(long id) {
        reschedules++;JSONObject i=getItem(id);JSONArray out=new JSONArray();
        for(JSONObject r:reminders.values())if(r.optLong("itemId")==id){
            long at=i.optLong("dueAt")-r.optInt("leadMinutes")*60000L;
            boolean enabled=!"done".equals(i.optString("status"))&&i.optLong("dueAt")>0&&at>System.currentTimeMillis();
            r.put("enabled",enabled).put("triggerAt",at);if(enabled)out.put(copy(r));
        }
        return out;
    }
    private UnsupportedOperationException outside() { return new UnsupportedOperationException("Operation outside appointment service fixture"); }
    public boolean purgeDeletedItem(long id) { throw outside(); }
    public JSONArray getCalendar(long start,long end) { throw outside(); }
    public boolean saveOccurrenceException(long id,long at,String state,JSONObject override) { throw outside(); }
    public boolean truncateSeriesBefore(long id,long at) { throw outside(); }
    public void deleteExceptionsFrom(long id,long at) { throw outside(); }
    public boolean setOccurrenceStatus(long id,long at,String status) { throw outside(); }
    public boolean setItemDeleted(long id,boolean deleted) { throw outside(); }
    public JSONObject getItemIncludingDeleted(long id) { throw outside(); }
}
