package com.jruiz.nexo.core;

import android.content.Context;

import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;
import com.jruiz.nexo.documents.DocumentFiles;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;

/** Lógica de dominio de Nexo. Toda la interpretación se realiza localmente. */
public class NexoService {
    private final Context context;
    private final DatabaseHelper db;

    public NexoService(Context context, DatabaseHelper db) {
        this.context = context.getApplicationContext();
        this.db = db;
        cleanupInterruptedDeletes();
        silenceArchiveReminders();
    }

    /** Retire alerts left on old place/document cards, retaining their stored rules and content. */
    private void silenceArchiveReminders() {
        try {
            JSONArray reminders=db.getActiveArchiveReminders();
            for(int i=0;i<reminders.length();i++) {
                JSONObject reminder=reminders.optJSONObject(i);if(reminder==null)continue;
                long id=reminder.optLong("id");
                ReminderScheduler.cancel(context,id);db.setReminderEnabled(id,false);
            }
        } catch (Exception ignored) {}
    }

    public JSONObject saveDocument(JSONObject in) {
        JSONObject out=new JSONObject();
        try {
            long id=in.optLong("id",0);
            JSONObject existing=id>0?db.getItem(id):new JSONObject();
            if(id>0 && (existing.length()==0 || !DocumentEntry.isDocument(existing.optString("type"))))
                return out.put("ok",false).put("error","No se encuentra este documento.");
            JSONObject fields=DocumentEntry.fields(in,existing);
            DocumentFiles.requireStored(new java.io.File(context.getFilesDir(),"attachments"),fields.optString("attachmentPath"));
            if(id>0) {
                fields.put("id",id);
                if(!db.updateItem(fields))return out.put("ok",false).put("error","No se pudo guardar el documento.");
                JSONArray reminders=db.getActiveRemindersForItem(id);
                for(int n=0;n<reminders.length();n++) {
                    JSONObject reminder=reminders.optJSONObject(n);if(reminder==null)continue;
                    long rid=reminder.optLong("id");ReminderScheduler.cancel(context,rid);db.setReminderEnabled(rid,false);
                }
            } else {
                fields.put("source","documents");id=db.insertItem(fields);
            }
            if(id>0 && !existing.optString("attachmentPath").equals(fields.optString("attachmentPath")))
                discardUnusedAttachment(existing.optString("attachmentPath"));
            return out.put("ok",id>0).put("id",id);
        } catch(IllegalArgumentException | java.io.IOException e) {
            try {return out.put("ok",false).put("error",e.getMessage());}catch(Exception ignored){return out;}
        } catch(Exception e) {
            try {return out.put("ok",false).put("error","No se pudo guardar el documento.");}catch(Exception ignored){return out;}
        }
    }

    public void discardUnusedAttachment(String path) {
        if(path==null || path.isBlank() || db.isAttachmentReferenced(path))return;
        try {DocumentFiles.requireStored(new java.io.File(context.getFilesDir(),"attachments"),path).delete();}
        catch(java.io.IOException ignored) {}
    }

    public JSONObject savePlace(JSONObject in) {
        JSONObject out=new JSONObject();
        try {
            long id=in.optLong("id",0);
            JSONObject existing=id>0?db.getItem(id):new JSONObject();
            if(id>0 && (existing.length()==0 || !PlaceEntry.isPlace(existing.optString("type")))) {
                return out.put("ok",false).put("error","No se encuentra este lugar.");
            }
            JSONObject fields=PlaceEntry.fields(in);
            if(id>0 && !in.has("label"))fields.put("subcategory",PlaceEntry.label(existing));
            if(id>0) {
                fields.put("id",id);
                if(!db.updateItem(fields))return out.put("ok",false).put("error","No se pudo guardar el lugar.");
                JSONArray reminders=db.getActiveRemindersForItem(id);
                for(int i=0;i<reminders.length();i++) {
                    JSONObject reminder=reminders.optJSONObject(i);if(reminder==null)continue;
                    long rid=reminder.optLong("id");ReminderScheduler.cancel(context,rid);db.setReminderEnabled(rid,false);
                }
            } else {
                fields.put("source","places");
                id=db.insertItem(fields);
            }
            return out.put("ok",id>0).put("id",id);
        } catch (IllegalArgumentException e) {
            try {return out.put("ok",false).put("error",e.getMessage());} catch(Exception ignored){return out;}
        } catch (Exception e) {
            try {return out.put("ok",false).put("error","No se pudo guardar el lugar.");} catch(Exception ignored){return out;}
        }
    }

    /** Si la app se cerró durante la ventana de Deshacer, completa el borrado al volver a abrir. */
    private void cleanupInterruptedDeletes() {
        try {
            JSONArray deleted = db.getDeletedItems();
            for (int i=0;i<deleted.length();i++) {
                JSONObject item = deleted.optJSONObject(i); if (item == null) continue;
                long id = item.optLong("id",0); String path = item.optString("attachmentPath","");
                if (id>0 && db.purgeDeletedItem(id) && !path.isBlank()) { discardUnusedAttachment(path); }
            }
        } catch (Exception ignored) {}
    }

    /** Clasificación local inmediata: no necesita Internet ni servicios externos. */
    public JSONObject classify(String text) {
        RuleClassifier.Result r = RuleClassifier.classify(text);
        applyLearnedCategory(text, r);
        return r.toJson();
    }

    public JSONObject classifyAppointment(String text) {
        RuleClassifier.Result r = AppointmentParser.parse(text);
        applyLearnedCategory(text, r);
        if ("Tareas".equals(r.category) || "Recordatorios".equals(r.category)) r.category = "Personal";
        return r.toJson();
    }

    /**
     * Las correcciones aprendidas son una ayuda secundaria. Nunca pueden sustituir una
     * categoría respaldada por señales locales claras del propio texto.
     */
    private void applyLearnedCategory(String text, RuleClassifier.Result r) {
        String learned = db.bestLearnedCategory(text);
        if (learned.isBlank()) return;
        boolean weakLocal = "Otros".equals(r.category) || r.confidence < .72;
        if (weakLocal) {
            boolean reviewAlreadyRequired = r.needsReview;
            r.category = learned;
            r.confidence = Math.max(r.confidence, .80);
            if (!reviewAlreadyRequired) r.reason = "Clasificación aprendida de correcciones anteriores";
        }
    }

    public JSONObject saveCapture(JSONObject in) {
        JSONObject out = new JSONObject();
        try {
            String body = in.optString("body", "").trim();
            String originalText = in.optString("originalText", "").trim();
            String classificationText = originalText.isBlank() ? body : originalText;
            boolean appointmentCapture = in.optBoolean("appointmentCapture", false);
            RuleClassifier.Result auto = appointmentCapture ? AppointmentParser.parse(classificationText) : RuleClassifier.classify(classificationText);
            applyLearnedCategory(classificationText, auto);
            if (appointmentCapture) {
                in.put("type", "event");
                if (!in.has("status")) in.put("status", "pending");
            }
            if (!in.has("title") || in.optString("title").isBlank()) in.put("title", auto.title);
            if (!in.has("type") || in.optString("type").isBlank()) in.put("type", auto.type);
            if (!in.has("category") || in.optString("category").isBlank()) in.put("category", auto.category);
            if (appointmentCapture && ("Tareas".equals(in.optString("category")) || "Recordatorios".equals(in.optString("category")))) in.put("category", "Personal");
            if (!in.has("dueAt")) {
                in.put("dueAt", auto.dueAt);
                in.put("hasTime", auto.hasTime);
            } else if (!in.has("hasTime")) {
                in.put("hasTime", false);
            }
            if (!in.has("durationMinutes") && auto.durationMinutes > 0) in.put("durationMinutes", auto.durationMinutes);
            if (!in.has("locationText")) in.put("locationText", auto.locationText);
            if (!in.has("body")) in.put("body", auto.description);
            if (!in.has("priority")) in.put("priority", auto.priority);
            if (!in.has("voiceEnabled")) in.put("voiceEnabled", auto.voiceRequested);
            if (!in.has("needsReview")) in.put("needsReview", auto.needsReview);
            String finalType = in.optString("type", "note");
            if ((finalType.equals("reminder") || finalType.equals("expiry")) && in.optLong("dueAt", 0) <= 0) in.put("needsReview", true);

            // Si la UI no especifica periodicidad, se usa la detectada localmente (cumpleaños => anual).
            // Si el usuario cambia manualmente a "No se repite", se respeta su corrección.
            String recurrence = RecurrenceEngine.normalize(in.has("recurrence") ? in.optString("recurrence", RecurrenceEngine.NONE) : auto.recurrence);
            in.put("recurrence", recurrence);
            if (!in.has("recurrenceRule")) in.put("recurrenceRule", recurrenceRuleFor(recurrence));
            if (RecurrenceEngine.isRecurring(recurrence)) {
                if (in.optLong("dueAt",0) <= 0) return out.put("ok",false).put("error","Una periodicidad necesita una fecha de inicio");
                long until = Math.max(0, in.optLong("recurrenceUntil",0));
                if (until > 0 && until < in.optLong("dueAt",0)) return out.put("ok",false).put("error","La fecha final debe ser posterior al inicio");
                in.put("recurrenceUntil", until);
            } else in.put("recurrenceUntil", 0);

            long id = db.insertItem(in);
            out.put("ok", true); out.put("id", id);

            long dueAt = in.optLong("dueAt", 0);
            JSONArray leads = normalizeLeadMinutes(in.optJSONArray("reminderLeadMinutes"));
            // Compatibilidad con formularios de versiones anteriores.
            if (leads.length() == 0 && in.optBoolean("createReminder", false)) {
                leads.put(Math.max(0, in.optInt("reminderLeadDays", 0)) * 1440);
            }
            if (leads.length() > 0 && dueAt > 0) {
                JSONArray created = createReminderRules(id, in.optString("title", "Recordatorio"), leads);
                out.put("reminderIds", created);
                if (created.length() > 0) out.put("reminderId", created.optLong(0)); // compatibilidad con el bridge anterior
            }
        } catch (Exception e) {
            try { out.put("ok", false); out.put("error", e.getMessage() == null ? "No se pudo guardar" : e.getMessage()); } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject updateItem(JSONObject in) {
        JSONObject out = new JSONObject();
        try {
            long id = in.optLong("id", 0);
            JSONObject existing = db.getItem(id);
            if (existing.length() == 0) return out.put("ok", false).put("error", "Elemento no encontrado");
            String recurrence = RecurrenceEngine.normalize(in.has("recurrence") ? in.optString("recurrence") : existing.optString("recurrence", RecurrenceEngine.NONE));
            if (in.has("recurrence") && !in.has("recurrenceRule")) in.put("recurrenceRule", recurrenceRuleFor(recurrence));
            long due = in.has("dueAt") ? in.optLong("dueAt", 0) : existing.optLong("dueAt", 0);
            long until = in.has("recurrenceUntil") ? in.optLong("recurrenceUntil", 0) : existing.optLong("recurrenceUntil", 0);
            if (RecurrenceEngine.isRecurring(recurrence)) {
                if (due <= 0) return out.put("ok", false).put("error", "Una periodicidad necesita una fecha de inicio");
                if (until > 0 && until < due) return out.put("ok", false).put("error", "La fecha final debe ser posterior al inicio");
                in.put("recurrenceUntil", until);
            } else if (in.has("recurrence")) in.put("recurrenceUntil", 0);
            boolean ok = db.updateItem(in);
            out.put("ok", ok);
            if (ok && (in.has("status") || in.has("dueAt") || in.has("recurrence") || in.has("recurrenceUntil"))) {
                out.put("rescheduled", refreshReminders(id));
            }
        } catch (Exception e) {
            try { out.put("ok", false); out.put("error", "No se pudo actualizar"); } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONArray getCalendar(long start, long end) { return db.getCalendar(start, end); }

    /** Edita una aparición recurrente con alcance single/following/all. */
    public JSONObject updateOccurrence(JSONObject in, String scope) {
        JSONObject out = new JSONObject();
        try {
            long seriesId=in.optLong("seriesId",0), occurrenceAt=in.optLong("occurrenceAt",0);
            JSONObject base=db.getItem(seriesId);
            if (base.length()==0 || occurrenceAt<=0) return out.put("ok",false).put("error","Serie no encontrada");
            String safeScope = scope==null?"single":scope;
            if ("single".equals(safeScope)) {
                JSONObject override=occurrenceOverride(in);
                boolean ok=db.saveOccurrenceException(seriesId, occurrenceAt, in.optString("occurrenceState","active"), override);
                refreshReminders(seriesId);
                return out.put("ok",ok).put("item",db.getOccurrence(seriesId,occurrenceAt));
            }
            if ("following".equals(safeScope)) {
                String nextRecurrence=RecurrenceEngine.normalize(in.optString("recurrence",base.optString("recurrence",RecurrenceEngine.NONE)));
                long nextDue=in.optLong("dueAt",occurrenceAt);
                long nextUntil=in.has("recurrenceUntil")?in.optLong("recurrenceUntil",base.optLong("recurrenceUntil",0)):base.optLong("recurrenceUntil",0);
                if(RecurrenceEngine.isRecurring(nextRecurrence)){
                    if(nextDue<=0) return out.put("ok",false).put("error","La serie necesita fecha de inicio");
                    if(nextUntil>0 && nextUntil<nextDue) return out.put("ok",false).put("error","La fecha final debe ser posterior al inicio");
                } else nextUntil=0;
                JSONArray reminders=db.getActiveRemindersForItem(seriesId);
                long oldUntil=base.optLong("recurrenceUntil",0);
                String oldRecurrence=base.optString("recurrence",RecurrenceEngine.NONE);
                if (!db.truncateSeriesBefore(seriesId, occurrenceAt)) return out.put("ok",false);
                db.deleteExceptionsFrom(seriesId, occurrenceAt);

                JSONObject next=new JSONObject(base.toString());
                JSONObject ov=occurrenceOverride(in);
                for (String key:new String[]{"title","body","category","type","dueAt","endAt","hasTime","allDay","timeZone","mapsUrl","recurrenceRule","participantsText","participantIds","durationMinutes","locationText","priority","favorite","needsReview"}) if(ov.has(key)) next.put(key,ov.get(key));
                if("task".equals(next.optString("type"))) next.put("status","pending");
                next.put("recurrence",nextRecurrence);
                next.put("recurrenceUntil",nextUntil);
                next.put("source","recurrence-split");
                long newId=db.insertItem(next);
                for(int i=0;i<reminders.length();i++){
                    JSONObject r=reminders.optJSONObject(i); if(r==null)continue;
                    long key=db.findNextOccurrence(newId,System.currentTimeMillis()-1); if(key<=0)continue;
                    JSONObject occ=db.getOccurrence(newId,key); int leadMinutes=r.has("leadMinutes")?r.optInt("leadMinutes",0):r.optInt("leadDays",0)*1440; long trigger=reminderTriggerMinutes(occ.optLong("dueAt",key),leadMinutes,occ.optBoolean("hasTime",false));
                    if(trigger>System.currentTimeMillis()){
                        long rid=db.insertReminderMinutes(newId,next.optString("title","Recordatorio"),trigger,leadMinutes,key);
                        ReminderScheduler.schedule(context,rid,next.optString("title","Recordatorio"),trigger);
                    }
                }
                refreshReminders(seriesId);
                return out.put("ok",true).put("newSeriesId",newId);
            }
            // Toda la serie.
            JSONObject payload=new JSONObject();
            payload.put("id",seriesId);
            JSONObject ov=occurrenceOverride(in);
            for (String key:new String[]{"title","body","category","type","endAt","allDay","timeZone","mapsUrl","recurrenceRule","participantsText","participantIds","durationMinutes","locationText","priority","favorite","needsReview"}) if(ov.has(key)) payload.put(key,ov.get(key));
            if(in.optBoolean("dateChanged",false) && ov.has("dueAt")){
                long oldBaseDue=base.optLong("dueAt",0);
                long shiftedBase = shiftWholeSeriesBase(oldBaseDue, occurrenceAt, ov.optLong("dueAt",0));
                payload.put("dueAt", shiftedBase);
                if(ov.has("hasTime")) payload.put("hasTime",ov.get("hasTime"));
                if(ov.has("allDay")) payload.put("allDay",ov.get("allDay"));
                long occurrenceEnd=ov.optLong("endAt",0);
                long occurrenceDue=ov.optLong("dueAt",0);
                if(occurrenceEnd>0 && occurrenceDue>0) payload.put("endAt", shiftedBase + Math.max(0L,occurrenceEnd-occurrenceDue));
                else {
                    long baseEnd=base.optLong("endAt",0);
                    if(baseEnd>0&&oldBaseDue>0)payload.put("endAt",shiftedBase+Math.max(0L,baseEnd-oldBaseDue));
                }
            }
            String allRecurrence=RecurrenceEngine.normalize(in.has("recurrence")?in.optString("recurrence"):base.optString("recurrence",RecurrenceEngine.NONE));
            long allDue=payload.has("dueAt")?payload.optLong("dueAt",0):base.optLong("dueAt",0);
            long allUntil=in.has("recurrenceUntil")?Math.max(0,in.optLong("recurrenceUntil")):base.optLong("recurrenceUntil",0);
            if(RecurrenceEngine.isRecurring(allRecurrence)){
                if(allDue<=0) return out.put("ok",false).put("error","La serie necesita fecha de inicio");
                if(allUntil>0 && allUntil<allDue) return out.put("ok",false).put("error","La fecha final debe ser posterior al inicio");
            } else allUntil=0;
            if(in.has("recurrence")) payload.put("recurrence",allRecurrence);
            if(in.has("recurrenceUntil")||in.has("recurrence")) payload.put("recurrenceUntil",allUntil);
            boolean ok=db.updateItem(payload); refreshReminders(seriesId); return out.put("ok",ok);
        } catch(Exception e){ try{return out.put("ok",false).put("error","No se pudo actualizar la serie");}catch(Exception ignored){return out;} }
    }

    public JSONObject deleteOccurrence(long seriesId,long occurrenceAt,String scope){
        JSONObject out=new JSONObject();
        try{
            if("all".equals(scope)) return deleteItem(seriesId);
            if("following".equals(scope)){
                boolean ok=db.truncateSeriesBefore(seriesId,occurrenceAt); db.deleteExceptionsFrom(seriesId,occurrenceAt); refreshReminders(seriesId); return out.put("ok",ok);
            }
            boolean ok=db.saveOccurrenceException(seriesId,occurrenceAt,"deleted",new JSONObject()); refreshReminders(seriesId); return out.put("ok",ok);
        }catch(Exception e){try{return out.put("ok",false);}catch(Exception ignored){return out;}}
    }

    public JSONObject setOccurrenceStatus(long seriesId,long occurrenceAt,String status){
        JSONObject out=new JSONObject();
        try{boolean ok=db.setOccurrenceStatus(seriesId,occurrenceAt,status);refreshReminders(seriesId);return out.put("ok",ok);}catch(Exception e){try{return out.put("ok",false);}catch(Exception ignored){return out;}}
    }

    private static String recurrenceRuleFor(String recurrence) {
        String r=RecurrenceEngine.normalize(recurrence);
        if (RecurrenceEngine.DAILY.equals(r)) return "FREQ=DAILY";
        if (RecurrenceEngine.WEEKLY.equals(r)) return "FREQ=WEEKLY";
        if (RecurrenceEngine.MONTHLY.equals(r)) return "FREQ=MONTHLY";
        if (RecurrenceEngine.BIMONTHLY.equals(r)) return "FREQ=MONTHLY;INTERVAL=2";
        if (RecurrenceEngine.QUARTERLY.equals(r)) return "FREQ=MONTHLY;INTERVAL=3";
        if (RecurrenceEngine.SEMIANNUAL.equals(r)) return "FREQ=MONTHLY;INTERVAL=6";
        if (RecurrenceEngine.ANNUAL.equals(r)) return "FREQ=YEARLY";
        return "";
    }

    private JSONObject occurrenceOverride(JSONObject in) throws Exception {
        JSONObject o=new JSONObject();
        for(String key:new String[]{"title","body","category","type","dueAt","endAt","hasTime","allDay","timeZone","mapsUrl","recurrenceRule","participantsText","participantIds","durationMinutes","locationText","priority","favorite","needsReview"}) if(in.has(key)) o.put(key,in.get(key));
        return o;
    }

    private JSONArray refreshReminders(long itemId){
        JSONArray active=db.getActiveRemindersForItem(itemId);
        for(int i=0;i<active.length();i++){JSONObject r=active.optJSONObject(i);if(r!=null)ReminderScheduler.cancel(context,r.optLong("id"));}
        JSONArray next=db.rescheduleRemindersForItem(itemId);
        for(int i=0;i<next.length();i++){JSONObject r=next.optJSONObject(i);if(r!=null)ReminderScheduler.schedule(context,r.optLong("id"),r.optString("title"),r.optLong("triggerAt"));}
        return next;
    }

    /** Eliminación reversible para ofrecer Deshacer en la interfaz. */
    public JSONObject deleteItem(long itemId) {
        JSONObject out = new JSONObject();
        try {
            JSONObject item = db.getItem(itemId);
            if (item.length() == 0) return out.put("ok", false).put("error", "Elemento no encontrado");
            JSONArray reminders = db.getActiveRemindersForItem(itemId);
            for (int i=0;i<reminders.length();i++) {
                JSONObject r = reminders.optJSONObject(i); if (r == null) continue;
                long rid = r.optLong("id");
                db.setReminderEnabled(rid, false);
                ReminderScheduler.cancel(context, rid);
            }
            boolean ok = db.setItemDeleted(itemId, true);
            JSONObject token = new JSONObject().put("itemId", itemId).put("reminders", reminders);
            out.put("ok", ok).put("undoToken", token.toString()).put("title", item.optString("title", "Elemento"));
        } catch (Exception e) {
            try { out.put("ok", false).put("error", "No se pudo eliminar"); } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject restoreItem(String tokenJson) {
        JSONObject out = new JSONObject();
        try {
            JSONObject token = new JSONObject(tokenJson);
            long itemId = token.optLong("itemId", 0);
            if (itemId <= 0 || !db.setItemDeleted(itemId, false)) return out.put("ok", false);
            JSONArray reminders = token.optJSONArray("reminders");
            if (reminders != null) {
                for (int i=0;i<reminders.length();i++) {
                    JSONObject r = reminders.optJSONObject(i); if (r == null) continue;
                    long rid = r.optLong("id"); long trigger = r.optLong("triggerAt");
                    if (trigger > System.currentTimeMillis()) {
                        db.setReminderEnabled(rid, true);
                        ReminderScheduler.schedule(context, rid, r.optString("title", "Recordatorio"), trigger);
                    }
                }
            }
            out.put("ok", true).put("item", db.getItem(itemId));
        } catch (Exception e) {
            try { out.put("ok", false); } catch (Exception ignored) {}
        }
        return out;
    }

    public JSONObject finalizeDelete(String tokenJson) {
        JSONObject out = new JSONObject();
        try {
            JSONObject token = new JSONObject(tokenJson);
            long itemId = token.optLong("itemId", 0);
            JSONObject item = db.getItemIncludingDeleted(itemId);
            if (itemId <= 0 || item.length() == 0 || item.optLong("deletedAt", 0) <= 0) return out.put("ok", false);
            String path = item.optString("attachmentPath", "");
            boolean ok = db.purgeDeletedItem(itemId);
            if (ok && !path.isBlank()) { discardUnusedAttachment(path); }
            out.put("ok", ok);
        } catch (Exception e) { try { out.put("ok", false); } catch (Exception ignored) {} }
        return out;
    }


    /** Anticipación exacta en minutos; permite desde minutos hasta meses. */
    private static long reminderTriggerMinutes(long dueAt, int leadMinutes, boolean hasTime) {
        if (dueAt <= 0) return 0;
        ZoneId zone = ZoneId.systemDefault();
        java.time.ZonedDateTime due = java.time.Instant.ofEpochMilli(dueAt).atZone(zone);
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);
        java.time.ZonedDateTime trigger = due.minusMinutes(Math.max(0, leadMinutes));
        if (trigger.isAfter(now)) return trigger.toInstant().toEpochMilli();
        if (due.isAfter(now)) return due.toInstant().toEpochMilli();
        if (!hasTime && due.toLocalDate().equals(now.toLocalDate())) return System.currentTimeMillis() + 60_000L;
        return trigger.toInstant().toEpochMilli();
    }

    private JSONArray createReminderRules(long itemId, String title, JSONArray rawLeads) {
        JSONArray created = new JSONArray();
        try {
            JSONObject item = db.getItem(itemId);
            long dueAt = item.optLong("dueAt", 0);
            if (dueAt <= 0) return created;
            boolean completed = "done".equals(item.optString("status"));
            String recurrence = item.optString("recurrence", RecurrenceEngine.NONE);
            long searchAfter = item.optBoolean("hasTime", false) ? System.currentTimeMillis()-1 : LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1;
            long occurrenceAt = !completed && RecurrenceEngine.isRecurring(recurrence) ? db.findNextOccurrence(itemId, searchAfter) : dueAt;
            if (occurrenceAt <= 0) return created;
            JSONObject occ = db.getOccurrence(itemId, occurrenceAt);
            long actualDue = occ.optLong("dueAt", occurrenceAt);
            boolean hasTime = occ.optBoolean("hasTime", item.optBoolean("hasTime", false));
            JSONArray leads = normalizeLeadMinutes(rawLeads);
            for (int i=0;i<leads.length();i++) {
                int leadMinutes = Math.max(0, leads.optInt(i,0));
                long trigger = reminderTriggerMinutes(actualDue, leadMinutes, hasTime);
                if (!completed && trigger <= System.currentTimeMillis()) continue;
                long rid = db.insertReminderMinutes(itemId, title, trigger, leadMinutes, occurrenceAt);
                if (completed) db.setReminderEnabled(rid, false);
                else ReminderScheduler.schedule(context, rid, title, trigger);
                created.put(rid);
            }
        } catch (Exception ignored) {}
        return created;
    }

    /** Sustituye los avisos de un elemento por una lista nueva. */
    public JSONObject replaceReminders(long itemId, JSONArray rawLeads) {
        JSONObject out = new JSONObject();
        try {
            JSONObject item = db.getItem(itemId);
            if (item.length()==0) return out.put("ok",false).put("error","Elemento no encontrado");
            JSONArray old = db.getReminderRulesForItem(itemId);
            for (int i=0;i<old.length();i++) { JSONObject r=old.optJSONObject(i); if(r!=null) ReminderScheduler.cancel(context,r.optLong("id")); }
            db.deleteRemindersForItem(itemId);
            JSONArray leads = normalizeLeadMinutes(rawLeads);
            JSONArray ids = createReminderRules(itemId, item.optString("title","Recordatorio"), leads);
            out.put("ok",true).put("reminderIds",ids).put("leadMinutes",leads);
        } catch (Exception e) {
            try { out.put("ok",false).put("error","No se pudieron actualizar los avisos"); } catch(Exception ignored) {}
        }
        return out;
    }

    private static JSONArray normalizeLeadMinutes(JSONArray input) {
        JSONArray out = new JSONArray();
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        if (input != null) {
            for (int i=0;i<input.length();i++) {
                int v = input.optInt(i,-1);
                if (v >= 0 && v <= 5_256_000) seen.add(v); // hasta diez años de anticipación
            }
        }
        for (Integer v:seen) out.put(v);
        return out;
    }

    /** Al editar toda una serie desde una aparición intermedia, desplaza el ancla original
     *  por la misma diferencia de días y usa la hora elegida. Así no desaparece el histórico. */
    private static long shiftWholeSeriesBase(long baseDueAt, long occurrenceAt, long editedDueAt) {
        if (baseDueAt <= 0 || occurrenceAt <= 0 || editedDueAt <= 0) return editedDueAt;
        ZoneId zone=ZoneId.systemDefault();
        java.time.ZonedDateTime base=java.time.Instant.ofEpochMilli(baseDueAt).atZone(zone);
        java.time.ZonedDateTime occurrence=java.time.Instant.ofEpochMilli(occurrenceAt).atZone(zone);
        java.time.ZonedDateTime edited=java.time.Instant.ofEpochMilli(editedDueAt).atZone(zone);
        long dayDelta=java.time.temporal.ChronoUnit.DAYS.between(occurrence.toLocalDate(),edited.toLocalDate());
        java.time.LocalDate shiftedDate=base.toLocalDate().plusDays(dayDelta);
        return shiftedDate.atTime(edited.toLocalTime()).atZone(zone).toInstant().toEpochMilli();
    }

    private static long defaultRecurrenceUntil(long dueAt) {
        ZoneId zone=ZoneId.systemDefault();
        LocalDate due=java.time.Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate();
        LocalDate end=LocalDate.of(2040,12,31); if(due.isAfter(end)) end=due;
        return end.atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
    }

}
