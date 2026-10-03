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

    public static JSONObject validatePayloadIntegrity(JSONObject payload) {
        JSONObject out = new JSONObject();
        try {
            if (payload == null) return out.put("ok", false).put("errors", new JSONArray().put("Datos vacíos."));
            String format = payload.optString("format", FORMAT);
            if (!FORMAT.equals(format)) {
                return out.put("ok", false).put("errors", new JSONArray().put("Formato de Nexo Familia no compatible."));
            }
            JSONArray events = payload.optJSONArray("events");
            if (events == null) {
                return out.put("ok", false).put("errors", new JSONArray().put("No se han recibido eventos."));
            }
            JSONArray rules = payload.optJSONArray("reminderRules");
            if (rules == null) rules = payload.optJSONArray("reminder_rules");
            if (rules == null) rules = new JSONArray();
            JSONArray errors = validatePayload(payload, events, rules);
            return out.put("ok", errors.length() == 0).put("errors", errors);
        } catch (Exception e) {
            try { return out.put("ok", false).put("errors", new JSONArray().put(safeMessage(e))); }
            catch (Exception ignored) { return out; }
        }
    }

    /**
     * Sincronización incremental. Importa UUID nuevos y vuelve a procesar un UUID
     * conocido solo cuando cambió el evento de Nexo Familia o sus reglas de aviso.
     * La mezcla de campos en importPayload preserva cualquier edición local que se
     * haya separado de la última versión remota conocida.
     */
    public static JSONObject importNewOnly(Context context, DatabaseHelper db, JSONObject payload) {
        JSONObject out = new JSONObject();
        try {
            JSONObject validation = validatePayloadIntegrity(payload);
            if (!validation.optBoolean("ok", false)) {
                return out.put("ok", false)
                        .put("error", "Los datos de Nexo Familia no superan la validación.")
                        .put("errors", validation.optJSONArray("errors"));
            }

            JSONArray sourceEvents = payload.optJSONArray("events");
            JSONArray sourceRules = payload.optJSONArray("reminderRules");
            if (sourceRules == null) sourceRules = payload.optJSONArray("reminder_rules");
            if (sourceRules == null) sourceRules = new JSONArray();

            Map<String, List<JSONObject>> rulesByEvent = new HashMap<>();
            for (int i = 0; i < sourceRules.length(); i++) {
                JSONObject rule = sourceRules.optJSONObject(i);
                if (rule == null) continue;
                rulesByEvent.computeIfAbsent(cleanString(rule, "event_id"), k -> new ArrayList<>()).add(rule);
            }

            JSONArray changedEvents = new JSONArray();
            LinkedHashSet<String> changedIds = new LinkedHashSet<>();
            int existing = 0, unchanged = 0, changedExisting = 0, newEvents = 0;

            for (int i = 0; i < sourceEvents.length(); i++) {
                JSONObject event = sourceEvents.optJSONObject(i);
                if (event == null) continue;
                String id = cleanString(event, "id");
                if (id.isBlank()) continue;

                long localId = db.getNexoFamiliaLocalItemId(id);
                JSONObject currentLocal = localId > 0L ? db.getItem(localId) : new JSONObject();
                if (localId <= 0L || currentLocal.length() == 0) {
                    changedEvents.put(event);
                    changedIds.add(id);
                    newEvents++;
                    continue;
                }

                existing++;
                JSONObject metadata = db.getNexoFamiliaMetadataForLocalItem(localId);
                JSONObject previousSource = metadata.optJSONObject("source");
                JSONArray previousRules = metadata.optJSONArray("reminderRules");
                List<JSONObject> remoteRules = rulesByEvent.getOrDefault(id, Collections.emptyList());
                boolean reservationMarkerMissing =
                        "vehicle_reservation".equals(cleanString(event,"event_kind"))
                                && !"vehicle_reservation".equals(currentLocal.optString("subcategory",""));

                if (sourceChanged(event, previousSource) || reminderRulesChanged(remoteRules, previousRules) || reservationMarkerMissing) {
                    changedEvents.put(event);
                    changedIds.add(id);
                    changedExisting++;
                } else {
                    unchanged++;
                }
            }

            if (changedEvents.length() == 0) {
                return out.put("ok", true)
                        .put("inserted", 0)
                        .put("updated", 0)
                        .put("updatedFromSource", 0)
                        .put("newFromSource", 0)
                        .put("unchangedExisting", unchanged)
                        .put("preserved", 0)
                        .put("reminders", 0)
                        .put("skipped", 0)
                        .put("alreadyExisting", existing)
                        .put("expectedEvents", 0)
                        .put("expectedReminderRules", 0)
                        .put("stats", db.getNexoFamiliaImportStats());
            }

            JSONArray changedRules = new JSONArray();
            for (int i = 0; i < sourceRules.length(); i++) {
                JSONObject rule = sourceRules.optJSONObject(i);
                if (rule == null) continue;
                if (changedIds.contains(cleanString(rule, "event_id"))) changedRules.put(rule);
            }

            JSONObject filtered = new JSONObject(payload.toString());
            filtered.put("events", changedEvents);
            filtered.put("reminderRules", changedRules);
            filtered.remove("reminder_rules");
            filtered.put("sourceAudit", new JSONObject()
                    .put("familyEventCount", changedEvents.length())
                    .put("reminderRuleCount", changedRules.length()));

            JSONObject result = importPayload(context, db, filtered);
            result.put("alreadyExisting", existing);
            result.put("unchangedExisting", unchanged);
            result.put("updatedFromSource", changedExisting);
            result.put("newFromSource", newEvents);
            result.put("sourceEventCount", sourceEvents.length());
            return result;
        } catch (Exception e) {
            try { return out.put("ok", false).put("error", safeMessage(e)); }
            catch (Exception ignored) { return out; }
        }
    }

    private static boolean sourceChanged(JSONObject current, JSONObject previous) {
        if (previous == null || previous.length() == 0) return true;
        String[] keys = {
                "title","description","start_at","end_at","all_day","start_date","end_date",
                "location","category","status","visibility","shared_with","participant_ids",
                "recurrence_rule","event_origin","social_type","venue_type","maps_url",
                "guest_count","guest_details","external_uid","external_source","time_zone","updated_at",
                "event_kind","vehicle_id","reservation_user_id","reservation_reason"
        };
        for (String key : keys) {
            if (!sourceValue(current, key).equals(sourceValue(previous, key))) return true;
        }
        return false;
    }

    private static String sourceValue(JSONObject source, String key) {
        if (source == null || !source.has(key) || source.isNull(key)) return "";
        Object value = source.opt(key);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
    }

    private static boolean reminderRulesChanged(List<JSONObject> current, JSONArray previous) {
        ArrayList<String> a = new ArrayList<>(), b = new ArrayList<>();
        if (current != null) {
            for (JSONObject rule : current) if (rule != null) a.add(reminderSignature(rule));
        }
        if (previous != null) {
            for (int i = 0; i < previous.length(); i++) {
                JSONObject rule = previous.optJSONObject(i);
                if (rule != null) b.add(reminderSignature(rule));
            }
        }
        Collections.sort(a);
        Collections.sort(b);
        return !a.equals(b);
    }

    private static String reminderSignature(JSONObject rule) {
        return cleanString(rule, "id") + "|" +
                cleanString(rule, "event_id") + "|" +
                Math.max(0, rule.optInt("minutes_before", 0)) + "|" +
                cleanString(rule, "status");
    }

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

            JSONArray payloadErrors = validatePayload(payload, events, rules);
            if (payloadErrors.length() > 0) {
                return out.put("ok", false)
                        .put("error", "La instantánea de Nexo Familia no supera la validación de integridad.")
                        .put("errors", payloadErrors);
            }

            Map<String,String> memberNames = new HashMap<>();
            JSONArray members = payload.optJSONArray("members");
            if (members != null) {
                for (int i=0;i<members.length();i++) {
                    JSONObject member=members.optJSONObject(i); if(member==null)continue;
                    String userId=member.optString("user_id","").trim();
                    String fullName=member.optString("full_name","").trim();
                    if(!userId.isEmpty()&&!fullName.isEmpty())memberNames.put(userId,fullName);
                }
            }

            Map<String, List<JSONObject>> rulesByEvent = new HashMap<>();
            for (int i=0;i<rules.length();i++) {
                JSONObject rule=rules.optJSONObject(i); if(rule==null)continue;
                String eventId=rule.optString("event_id","");
                rulesByEvent.computeIfAbsent(eventId,k->new ArrayList<>()).add(rule);
            }

            final boolean deferAlarmSideEffects = db.getWritableDatabase().inTransaction();

            for (int i=0;i<events.length();i++) {
                JSONObject event=events.optJSONObject(i);
                if(event==null){skipped++;continue;}
                String sourceId=event.optString("id","").trim();
                if(sourceId.isEmpty()){errors.put("Evento sin id en la posición "+i);skipped++;continue;}
                try {
                    JSONObject remoteLocal=toLocalEvent(event,memberNames);
                    long existing=db.getNexoFamiliaLocalItemId(sourceId);
                    long localId;
                    JSONObject effectiveLocal;
                    if(existing>0 && db.getItem(existing).length()>0) {
                        JSONObject currentLocal=db.getItem(existing);
                        JSONObject metadata=db.getNexoFamiliaMetadataForLocalItem(existing);
                        JSONObject previousSource=metadata.optJSONObject("source");
                        JSONObject previousLocal=(previousSource==null||previousSource.length()==0)
                                ?new JSONObject()
                                :toLocalEvent(previousSource,memberNames);
                        JSONObject patch=mergeRemoteChanges(remoteLocal,previousLocal,currentLocal);
                        if("vehicle_reservation".equals(remoteLocal.optString("subcategory"))
                                && !"vehicle_reservation".equals(currentLocal.optString("subcategory"))) {
                            patch.put("subcategory","vehicle_reservation");
                        }
                        if(patch.length()>0) {
                            patch.put("id",existing);
                            if(!db.updateItem(patch))throw new IllegalStateException("No se pudo actualizar el evento local");
                            updated++;
                        }
                        localId=existing;
                        effectiveLocal=db.getItem(existing);
                    } else {
                        localId=db.insertItem(remoteLocal);
                        if(localId<=0)throw new IllegalStateException("No se pudo crear el evento local");
                        inserted++;
                        effectiveLocal=db.getItem(localId);
                    }
                    db.rememberNexoFamiliaEvent(sourceId,localId,event);
                    preserved++;

                    JSONArray oldReminderIds=db.deleteNexoFamiliaImportedReminders(sourceId);
                    if(!deferAlarmSideEffects) {
                        for(int n=0;n<oldReminderIds.length();n++) ReminderScheduler.cancel(context,oldReminderIds.optLong(n));
                    }

                    for(JSONObject rule:rulesByEvent.getOrDefault(sourceId,Collections.emptyList())) {
                        int minutes=Math.max(0,rule.optInt("minutes_before",0));
                        String ruleId=rule.optString("id","").trim();
                        String sourceStatus=rule.optString("status","pending");
                        long localReminderId=0;
                        if("pending".equals(sourceStatus)) {
                            long occurrence=nextOccurrence(db,localId,effectiveLocal);
                            long trigger=reminderTrigger(occurrence,minutes,effectiveLocal.optBoolean("hasTime",false));
                            if(trigger>System.currentTimeMillis()) {
                                localReminderId=db.insertReminderMinutes(localId,effectiveLocal.optString("title","Recordatorio"),trigger,minutes,occurrence);
                                if(localReminderId>0) {
                                    if(!deferAlarmSideEffects) {
                                        ReminderScheduler.schedule(context,localReminderId,effectiveLocal.optString("title","Recordatorio"),trigger);
                                    }
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
            int verifiedEvents = 0;
            for (int i=0;i<events.length();i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) continue;
                String sourceId = cleanString(event, "id");
                long localId = db.getNexoFamiliaLocalItemId(sourceId);
                if (localId > 0 && db.getItem(localId).length() > 0) verifiedEvents++;
            }

            int verifiedRules = 0;
            for (int i=0;i<rules.length();i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) continue;
                if (db.hasNexoFamiliaReminderMapping(cleanString(rule, "id"))) verifiedRules++;
            }

            JSONObject stats = db.getNexoFamiliaImportStats();
            boolean complete = errors.length()==0 &&
                    skipped==0 &&
                    preserved==events.length() &&
                    verifiedEvents==events.length() &&
                    verifiedRules==rules.length() &&
                    stats.optLong("brokenMappings", 0L)==0L &&
                    stats.optLong("metadataComplete", 0L)>=events.length();

            if (!complete) {
                errors.put("Verificación final incompleta: eventos " + verifiedEvents + "/" + events.length() +
                        ", reglas " + verifiedRules + "/" + rules.length() + ".");
            }

            out.put("ok", complete)
               .put("inserted",inserted)
               .put("updated",updated)
               .put("preserved",preserved)
               .put("reminders",reminderCount)
               .put("verifiedEvents",verifiedEvents)
               .put("verifiedReminderRules",verifiedRules)
               .put("expectedEvents",events.length())
               .put("expectedReminderRules",rules.length())
               .put("skipped",skipped)
               .put("errors",errors)
               .put("stats",stats);
        } catch(Exception e) {
            try {out.put("ok",false).put("error",safeMessage(e)).put("errors",errors);} catch(Exception ignored){}
        }
        return out;
    }

    private static JSONArray validatePayload(JSONObject payload, JSONArray events, JSONArray rules) {
        JSONArray errors = new JSONArray();
        LinkedHashSet<String> eventIds = new LinkedHashSet<>();
        LinkedHashSet<String> ruleIds = new LinkedHashSet<>();

        JSONObject audit = payload.optJSONObject("sourceAudit");
        if (audit != null) {
            int expectedEvents = audit.optInt("familyEventCount", events.length());
            int expectedRules = audit.optInt("reminderRuleCount", rules.length());
            if (expectedEvents != events.length()) {
                errors.put("El snapshot declara " + expectedEvents + " eventos pero contiene " + events.length() + ".");
            }
            if (expectedRules != rules.length()) {
                errors.put("El snapshot declara " + expectedRules + " reglas pero contiene " + rules.length() + ".");
            }
        }

        for (int i=0;i<events.length();i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                errors.put("Evento inválido en posición " + i + ".");
                continue;
            }
            String id = cleanString(event, "id");
            if (id.isBlank()) errors.put("Evento sin UUID en posición " + i + ".");
            else if (!eventIds.add(id)) errors.put("UUID de evento duplicado: " + id + ".");
            if (cleanString(event, "title").isBlank()) errors.put("Evento sin título: " + id + ".");
            boolean allDay = event.optBoolean("all_day", false);
            String start = allDay ? cleanString(event, "start_date") : cleanString(event, "start_at");
            if (start.isBlank()) errors.put("Evento sin inicio válido: " + id + ".");
            if (cleanString(event, "time_zone").isBlank()) errors.put("Evento sin zona horaria: " + id + ".");
        }

        for (int i=0;i<rules.length();i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                errors.put("Regla de aviso inválida en posición " + i + ".");
                continue;
            }
            String id = cleanString(rule, "id");
            String eventId = cleanString(rule, "event_id");
            if (id.isBlank()) errors.put("Regla de aviso sin UUID en posición " + i + ".");
            else if (!ruleIds.add(id)) errors.put("UUID de regla duplicado: " + id + ".");
            if (eventId.isBlank() || !eventIds.contains(eventId)) {
                errors.put("Regla " + id + " apunta a un evento no incluido: " + eventId + ".");
            }
        }
        return errors;
    }

    public static long sourceStartMillis(JSONObject event) {
        if (event == null) return 0L;
        boolean allDay = event.optBoolean("all_day", false);
        ZoneId zone = safeZone(cleanString(event, "time_zone"));
        return allDay
                ? parseDate(cleanString(event, "start_date"), zone)
                : parseTimestamp(cleanString(event, "start_at"), zone);
    }

    private static JSONObject toLocalEvent(JSONObject event, Map<String,String> memberNames) throws Exception {
        boolean allDay=event.optBoolean("all_day",false);
        ZoneId zone=safeZone(cleanString(event,"time_zone"));
        long start=allDay ? parseDate(event.optString("start_date",""),zone) : parseTimestamp(event.optString("start_at",""),zone);
        long end=allDay ? parseDate(event.optString("end_date",""),zone) : parseTimestamp(event.optString("end_at",""),zone);
        if(start<=0)throw new IllegalArgumentException("Falta fecha de inicio válida");
        if(end<=0)end=start;

        int duration=0;
        if(end>start) duration=(int)Math.min(Integer.MAX_VALUE,Math.max(0,(end-start)/60000L));
        if(allDay && !event.optString("end_date","").isBlank()) {
            try {
                long days=ChronoUnit.DAYS.between(LocalDate.parse(event.optString("start_date")),LocalDate.parse(event.optString("end_date")));
                if(days>0)duration=(int)Math.min(Integer.MAX_VALUE,days*1440L);
            } catch(Exception ignored){}
        }

        String recurrenceRule=cleanString(event,"recurrence_rule");
        String recurrence=mapRecurrence(recurrenceRule);
        long recurrenceUntil=parseUntil(recurrenceRule,zone);
        String status=mapStatus(cleanString(event,"status"));
        boolean vehicleReservation="vehicle_reservation".equals(cleanString(event,"event_kind"));

        JSONObject local=new JSONObject();
        local.put("type","event");
        local.put("title",nonBlank(cleanString(event,"title"),"Evento de Nexo Familia"));
        local.put("body",cleanString(event,"description"));
        local.put("originalText","");
        local.put("category",canonicalCategory(cleanString(event,"category")));
        local.put("subcategory",vehicleReservation?"vehicle_reservation":"");
        local.put("dueAt",start);
        local.put("endAt",end);
        local.put("hasTime",!allDay);
        local.put("allDay",allDay);
        local.put("timeZone",zone.getId());
        local.put("durationMinutes",duration);
        local.put("locationText",cleanString(event,"location"));
        local.put("mapsUrl",cleanString(event,"maps_url"));
        local.put("recurrenceRule",cleanString(event,"recurrence_rule"));
        local.put("participantsText",participantNames(event.optJSONArray("participant_ids"),memberNames));
        local.put("priority",1);
        local.put("favorite",false);
        local.put("source","nexo-familia");
        local.put("status",status);
        // El estado recibido de Nexo Familia se conserva literalmente.\n        // La sincronización posterior no reescribe eventos ya importados.\n        local.put("statusManual",true);
        local.put("needsReview",false);
        local.put("recurrence",recurrence);
        local.put("recurrenceUntil",recurrenceUntil);
        local.put("voiceEnabled",false);
        return local;
    }

    private static final String[] REMOTE_SYNC_FIELDS = {
            "title","body","category","subcategory","dueAt","endAt","hasTime","allDay","timeZone",
            "durationMinutes","locationText","mapsUrl","recurrenceRule","participantsText",
            "status","recurrence","recurrenceUntil"
    };

    /**
     * Tres vías: remoto nuevo vs remoto anterior vs estado local actual.
     * Solo aplica un campo remoto si ese campo no fue modificado localmente desde
     * la última sincronización. Favorito, prioridad, voz y otros campos locales no
     * forman parte de REMOTE_SYNC_FIELDS y nunca se pisan.
     */
    private static JSONObject mergeRemoteChanges(JSONObject remote, JSONObject previous, JSONObject current) {
        JSONObject patch = new JSONObject();
        try {
            if (previous == null || previous.length() == 0) {
                for (String key : REMOTE_SYNC_FIELDS) {
                    if (remote.has(key)) patch.put(key, remote.opt(key));
                }
                if (patch.has("status")) patch.put("statusManual", true);
                return patch;
            }
            for (String key : REMOTE_SYNC_FIELDS) {
                if (!remote.has(key)) continue;
                boolean remoteChanged = !jsonValueEquals(remote, previous, key);
                boolean localUntouched = jsonValueEquals(current, previous, key);
                if (remoteChanged && localUntouched) patch.put(key, remote.opt(key));
            }
            if (patch.has("status")) patch.put("statusManual", true);
        } catch (Exception ignored) {}
        return patch;
    }

    private static boolean jsonValueEquals(JSONObject a, JSONObject b, String key) {
        Object av = a == null || !a.has(key) || a.isNull(key) ? null : a.opt(key);
        Object bv = b == null || !b.has(key) || b.isNull(key) ? null : b.opt(key);
        if (av == null || av == JSONObject.NULL) av = null;
        if (bv == null || bv == JSONObject.NULL) bv = null;
        if (av == null || bv == null) return av == bv;
        if (av instanceof Number && bv instanceof Number) {
            return Double.compare(((Number) av).doubleValue(), ((Number) bv).doubleValue()) == 0;
        }
        return String.valueOf(av).equals(String.valueOf(bv));
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
        if("cancelled".equalsIgnoreCase(source))return "cancelled";
        if("completed".equalsIgnoreCase(source))return "done";
        return "pending";
    }

    private static String participantNames(JSONArray ids, Map<String,String> memberNames) {
        if(ids==null||ids.length()==0)return "";
        LinkedHashSet<String> names=new LinkedHashSet<>();
        for(int i=0;i<ids.length();i++) {
            String id=ids.optString(i,"").trim();
            String name=memberNames==null?null:memberNames.get(id);
            if(name!=null&&!name.isBlank())names.add(name);
        }
        return String.join(", ",names);
    }

    private static String canonicalCategory(String raw) {
        String value=nonBlank(raw,"Otro").trim();
        Set<String> allowed=new LinkedHashSet<>(Arrays.asList(
                "Familia","Cumpleaños","Santo / Onomástica","Salud / Médico","Colegio / Estudios","Trabajo",
                "Viaje","Vacaciones","Casa","Vehículos / ITV","Seguros","Trámites","Tarea / Recado","Compras",
                "Ocio / Social","Deporte","Recordatorio","Otro"));
        return allowed.contains(value)?value:"Otro";
    }

    private static String cleanString(JSONObject source, String key) {
        if (source == null || key == null || !source.has(key) || source.isNull(key)) return "";
        Object value = source.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        String text = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private static String nonBlank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null||m.isBlank()?e.getClass().getSimpleName():m;}
}
