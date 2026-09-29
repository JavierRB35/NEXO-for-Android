package com.jruiz.nexo.migration;

import android.content.Context;

import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class NexoFamiliaImporter {
    public static final String FORMAT = "nexo-familia-events-v1";
    private NexoFamiliaImporter() {}

    public static JSONObject importPayload(Context context, DatabaseHelper db, JSONObject payload) {
        JSONObject out = new JSONObject();
        JSONArray errors = new JSONArray();
        int inserted = 0, updated = 0, preserved = 0, reminderCount = 0, skipped = 0;
        try {
            if (payload == null) return out.put("ok", false).put("error", "Archivo vacío.");
            String format = payload.optString("format", FORMAT);
            if (!FORMAT.equals(format)) return out.put("ok", false).put("error", "Formato de Nexo Familia no compatible.");
            JSONArray events = payload.optJSONArray("events");
            if (events == null) return out.put("ok", false).put("error", "El archivo no contiene eventos.");
            JSONArray rules = payload.optJSONArray("reminderRules");
            if (rules == null) rules = payload.optJSONArray("reminder_rules");
            if (rules == null) rules = new JSONArray();

            Map<String, List<JSONObject>> rulesByEvent = new HashMap<>();
            for (int i=0;i<rules.length();i++) {
                JSONObject rule=rules.optJSONObject(i); if(rule==null)continue;
                String eventId=rule.optString("event_id","");
                rulesByEvent.computeIfAbsent(eventId,k->new ArrayList<>()).add(rule);
            }

            for (int i=0;i<events.length();i++) {
                JSONObject event=events.optJSONObject(i);
                if(event==null){skipped++;continue;}
                String sourceId=event.optString("id","").trim();
                if(sourceId.isEmpty()){errors.put("Evento sin id en la posición "+i);skipped++;continue;}
                try {
                    JSONObject local=toLocalEvent(event);
                    long existing=db.getNexoFamiliaLocalItemId(sourceId);
                    long localId;
                    if(existing>0 && db.getItem(existing).length()>0) {
                        local.put("id",existing);
                        if(!db.updateItem(local))throw new IllegalStateException("No se pudo actualizar el evento local");
                        if(local.has("status")) db.updateItem(new JSONObject().put("id",existing).put("status",local.optString("status")));
                        localId=existing;updated++;
                    } else {
                        localId=db.insertItem(local);
                        if(localId<=0)throw new IllegalStateException("No se pudo crear el evento local");
                        inserted++;
                    }
                    db.rememberNexoFamiliaEvent(sourceId,localId,event);
                    preserved++;

                    JSONArray oldReminderIds=db.deleteNexoFamiliaImportedReminders(sourceId);
                    for(int n=0;n<oldReminderIds.length();n++) ReminderScheduler.cancel(context,oldReminderIds.optLong(n));

                    for(JSONObject rule:rulesByEvent.getOrDefault(sourceId,Collections.emptyList())) {
                        int minutes=Math.max(0,rule.optInt("minutes_before",0));
                        String ruleId=rule.optString("id","").trim();
                        String sourceStatus=rule.optString("status","pending");
                        long localReminderId=0;
                        if("pending".equals(sourceStatus)) {
                            long occurrence=nextOccurrence(db,localId,local);
                            long trigger=reminderTrigger(occurrence,minutes,local.optBoolean("hasTime",false));
                            if(trigger>System.currentTimeMillis()) {
                                localReminderId=db.insertReminderMinutes(localId,local.optString("title","Recordatorio"),trigger,minutes,occurrence);
                                if(localReminderId>0) {
                                    ReminderScheduler.schedule(context,localReminderId,local.optString("title","Recordatorio"),trigger);
                                    reminderCount++;
                                }
                            }
                        }
                        if(!ruleId.isEmpty()) db.rememberNexoFamiliaReminder(ruleId,sourceId,localId,localReminderId,minutes,sourceStatus,rule);
                    }
                } catch(Exception e) {
                    errors.put("Evento "+sourceId+": "+safeMessage(e));
                    skipped++;
                }
            }
            out.put("ok", errors.length()==0)
               .put("inserted",inserted)
               .put("updated",updated)
               .put("preserved",preserved)
               .put("reminders",reminderCount)
               .put("skipped",skipped)
               .put("errors",errors)
               .put("stats",db.getNexoFamiliaImportStats());
        } catch(Exception e) {
            try {out.put("ok",false).put("error",safeMessage(e)).put("errors",errors);} catch(Exception ignored){}
        }
        return out;
    }

    private static JSONObject toLocalEvent(JSONObject event) throws Exception {
        boolean allDay=event.optBoolean("all_day",false);
        ZoneId zone=safeZone(event.optString("time_zone","Europe/Madrid"));
        long start=allDay ? parseDate(event.optString("start_date",""),zone) : parseTimestamp(event.optString("start_at",""),zone);
        long end=allDay ? parseDate(event.optString("end_date",""),zone) : parseTimestamp(event.optString("end_at",""),zone);
        if(start<=0)throw new IllegalArgumentException("Falta fecha de inicio válida");

        int duration=0;
        if(end>start) duration=(int)Math.min(Integer.MAX_VALUE,Math.max(0,(end-start)/60000L));
        if(allDay && !event.optString("end_date","").isBlank()) {
            try {
                long days=ChronoUnit.DAYS.between(LocalDate.parse(event.optString("start_date")),LocalDate.parse(event.optString("end_date")));
                if(days>0)duration=(int)Math.min(Integer.MAX_VALUE,days*1440L);
            } catch(Exception ignored){}
        }

        String recurrence=mapRecurrence(event.optString("recurrence_rule",""));
        long recurrenceUntil=parseUntil(event.optString("recurrence_rule",""),zone);
        String status=mapStatus(event.optString("status","pending"));

        JSONObject local=new JSONObject();
        local.put("type","event");
        local.put("title",nonBlank(event.optString("title"),"Evento de Nexo Familia"));
        local.put("body",event.optString("description",""));
        local.put("originalText","");
        local.put("category",canonicalCategory(event.optString("category","Otro")));
        local.put("dueAt",start);
        local.put("hasTime",!allDay);
        local.put("durationMinutes",duration);
        local.put("locationText",event.optString("location",""));
        local.put("priority",1);
        local.put("favorite",false);
        local.put("source","nexo-familia");
        local.put("status",status);
        local.put("needsReview",false);
        local.put("recurrence",recurrence);
        local.put("recurrenceUntil",recurrenceUntil);
        local.put("voiceEnabled",false);
        return local;
    }

    private static long nextOccurrence(DatabaseHelper db,long localId,JSONObject local) {
        if(!"none".equals(local.optString("recurrence","none"))) {
            long next=db.findNextOccurrence(localId,System.currentTimeMillis()-1);
            if(next>0)return next;
        }
        return local.optLong("dueAt",0);
    }

    private static long reminderTrigger(long occurrenceAt,int minutesBefore,boolean hasTime) {
        if(occurrenceAt<=0)return 0;
        long trigger=occurrenceAt-(Math.max(0,minutesBefore)*60000L);
        long now=System.currentTimeMillis();
        if(trigger>now)return trigger;
        if(occurrenceAt>now)return occurrenceAt;
        if(!hasTime) {
            LocalDate eventDay=Instant.ofEpochMilli(occurrenceAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if(eventDay.equals(LocalDate.now()))return now+60000L;
        }
        return 0;
    }

    private static ZoneId safeZone(String raw) {
        try{return ZoneId.of(nonBlank(raw,"Europe/Madrid"));}catch(Exception e){return ZoneId.systemDefault();}
    }

    private static long parseDate(String raw,ZoneId zone) {
        if(raw==null||raw.isBlank())return 0;
        try{return LocalDate.parse(raw.trim()).atTime(9,0).atZone(zone).toInstant().toEpochMilli();}catch(Exception e){return 0;}
    }

    private static long parseTimestamp(String raw,ZoneId zone) {
        if(raw==null||raw.isBlank())return 0;
        String v=raw.trim().replace(' ','T');
        if(v.matches(".*[+-]\\d{2}$"))v=v+":00";
        try{return OffsetDateTime.parse(v).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return Instant.parse(v).toEpochMilli();}catch(Exception ignored){}
        try{return LocalDateTime.parse(v).atZone(zone).toInstant().toEpochMilli();}catch(Exception ignored){}
        return 0;
    }

    private static String mapRecurrence(String rrule) {
        String r=rrule==null?"":rrule.toUpperCase(Locale.ROOT);
        if(r.isBlank())return "none";
        if(r.contains("FREQ=DAILY"))return "daily";
        if(r.contains("FREQ=WEEKLY"))return "weekly";
        if(r.contains("FREQ=YEARLY"))return "annual";
        if(r.contains("FREQ=MONTHLY")) {
            int interval=rruleInt(r,"INTERVAL",1);
            if(interval==2)return "bimonthly";
            if(interval==3)return "quarterly";
            if(interval==6)return "semiannual";
            return "monthly";
        }
        return "none";
    }

    private static int rruleInt(String rule,String key,int fallback) {
        try {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|;)"+key+"=(\\d+)(?:;|$)").matcher(rule);
            return m.find()?Integer.parseInt(m.group(1)):fallback;
        } catch(Exception e){return fallback;}
    }

    private static long parseUntil(String rrule,ZoneId zone) {
        if(rrule==null||rrule.isBlank())return 0;
        try {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|;)UNTIL=(\\d{8})").matcher(rrule.toUpperCase(Locale.ROOT));
            if(!m.find())return 0;
            String v=m.group(1);
            LocalDate d=LocalDate.of(Integer.parseInt(v.substring(0,4)),Integer.parseInt(v.substring(4,6)),Integer.parseInt(v.substring(6,8)));
            return d.atTime(23,59,59).atZone(zone).toInstant().toEpochMilli();
        } catch(Exception e){return 0;}
    }

    private static String mapStatus(String source) {
        if("completed".equalsIgnoreCase(source)||"cancelled".equalsIgnoreCase(source))return "done";
        return "pending";
    }

    private static String canonicalCategory(String raw) {
        String value=nonBlank(raw,"Otro").trim();
        Set<String> allowed=new LinkedHashSet<>(Arrays.asList(
                "Familia","Cumpleaños","Santo / Onomástica","Salud / Médico","Colegio / Estudios","Trabajo",
                "Viaje","Vacaciones","Casa","Vehículos / ITV","Seguros","Trámites","Tarea / Recado","Compras",
                "Ocio / Social","Deporte","Recordatorio","Otro"));
        return allowed.contains(value)?value:"Otro";
    }

    private static String nonBlank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null||m.isBlank()?e.getClass().getSimpleName():m;}
}
