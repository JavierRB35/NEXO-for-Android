package com.jruiz.nexo.calendar;

import android.content.Context;
import android.net.Uri;

import com.jruiz.nexo.exchange.NexoExchangeManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Importador deliberadamente conservador de un VEVENT iCalendar (.ics). */
public final class CalendarImporter {
    private CalendarImporter() {}

    public static JSONObject read(Context context, Uri uri) throws Exception {
        String raw=new String(NexoExchangeManager.readBytes(context,uri,512*1024),StandardCharsets.UTF_8);
        List<String> lines=unfold(raw); boolean inside=false; Map<String,List<String>> props=new LinkedHashMap<>(); List<String> alarmTriggers=new ArrayList<>(); boolean inAlarm=false;
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.equals("BEGIN:VEVENT")){inside=true;continue;}if(u.equals("END:VEVENT"))break;if(!inside)continue;if(u.equals("BEGIN:VALARM")){inAlarm=true;continue;}if(u.equals("END:VALARM")){inAlarm=false;continue;}int p=line.indexOf(':');if(p<=0)continue;String keyPart=line.substring(0,p),value=line.substring(p+1);String key=keyPart.split(";",2)[0].toUpperCase(Locale.ROOT);if(inAlarm&&key.equals("TRIGGER")){alarmTriggers.add(value.trim());continue;}props.computeIfAbsent(key,k->new ArrayList<>()).add(keyPart+":"+value);}
        if(props.isEmpty())throw new IllegalArgumentException("El archivo no contiene un evento compatible");
        JSONObject item=new JSONObject();String title=decode(valueOf(props,"SUMMARY"));if(title.isBlank())title="Evento importado";String body=decode(valueOf(props,"DESCRIPTION"));String location=decode(valueOf(props,"LOCATION"));
        item.put("title",title);item.put("body",body);item.put("originalText",String.join(" ",Arrays.asList(title,body,location)).trim());item.put("type","event");item.put("locationText",location);item.put("source","ics-import");item.put("needsReview",false);
        ParsedDate start=parseDate(propertyLine(props,"DTSTART"));if(start.millis>0){item.put("dueAt",start.millis);item.put("hasTime",start.hasTime);}else{item.put("dueAt",0);item.put("hasTime",false);}
        ParsedDate end=parseDate(propertyLine(props,"DTEND"));if(start.millis>0&&end.millis>start.millis&&start.hasTime)item.put("durationMinutes",Math.max(0,(int)((end.millis-start.millis)/60000L)));
        String rrule=valueOf(props,"RRULE").toUpperCase(Locale.ROOT);String recurrence=recurrence(rrule);item.put("recurrence",recurrence);long until=parseUntil(rrule,start,recurrence);if(until>0)item.put("recurrenceUntil",until);
        JSONArray leads=new JSONArray();for(String t:alarmTriggers){int m=leadMinutes(t);if(m>=0)leads.put(m);}item.put("reminderLeadMinutes",leads);
        JSONObject root=new JSONObject();root.put("format","ics-import");root.put("version",1);root.put("entity","item");root.put("createdAt",System.currentTimeMillis());root.put("data",item);return root;
    }

    public static JSONObject preview(JSONObject root){JSONObject out=new JSONObject();try{JSONObject d=root.optJSONObject("data");if(d==null)d=new JSONObject();out.put("format","ics");out.put("entity","item");out.put("title",d.optString("title","Evento"));out.put("body",d.optString("body",""));out.put("category","Calendario (.ics)");out.put("dueAt",d.optLong("dueAt",0));out.put("hasTime",d.optBoolean("hasTime",false));out.put("locationText",d.optString("locationText",""));}catch(Exception ignored){}return out;}

    private static List<String> unfold(String raw){String normalized=raw.replace("\r\n","\n").replace('\r','\n');List<String> out=new ArrayList<>();for(String line:normalized.split("\n",-1)){if((line.startsWith(" ")||line.startsWith("\t"))&&!out.isEmpty())out.set(out.size()-1,out.get(out.size()-1)+line.substring(1));else out.add(line);}return out;}
    private static String propertyLine(Map<String,List<String>> p,String key){List<String> l=p.get(key);return l==null||l.isEmpty()?"":l.get(0);}
    private static String valueOf(Map<String,List<String>> p,String key){String line=propertyLine(p,key);int i=line.indexOf(':');return i<0?"":line.substring(i+1);}
    private static String decode(String s){return s.replace("\\n","\n").replace("\\N","\n").replace("\\,",",").replace("\\;",";").replace("\\\\","\\").trim();}
    private static ParsedDate parseDate(String line){if(line==null||line.isBlank())return new ParsedDate(0,false);try{int c=line.indexOf(':');if(c<0)return new ParsedDate(0,false);String head=line.substring(0,c).toUpperCase(Locale.ROOT),v=line.substring(c+1).trim();ZoneId zone=ZoneId.systemDefault();int tz=head.indexOf("TZID=");if(tz>=0){String z=head.substring(tz+5);int semi=z.indexOf(';');if(semi>=0)z=z.substring(0,semi);try{zone=ZoneId.of(z);}catch(Exception ignored){}}boolean dateOnly=head.contains("VALUE=DATE")||v.matches("\\d{8}");if(dateOnly){LocalDate d=LocalDate.parse(v.substring(0,8),DateTimeFormatter.BASIC_ISO_DATE);return new ParsedDate(d.atStartOfDay(zone).toInstant().toEpochMilli(),false);}if(v.endsWith("Z")){DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'",Locale.US);LocalDateTime dt=LocalDateTime.parse(v,f);return new ParsedDate(dt.toInstant(ZoneOffset.UTC).toEpochMilli(),true);}DateTimeFormatter f=DateTimeFormatter.ofPattern(v.length()>=15?"yyyyMMdd'T'HHmmss":"yyyyMMdd'T'HHmm",Locale.US);LocalDateTime dt=LocalDateTime.parse(v,f);return new ParsedDate(dt.atZone(zone).toInstant().toEpochMilli(),true);}catch(Exception e){return new ParsedDate(0,false);}}
    private static String recurrence(String r){if(r.contains("BYDAY=")||r.contains("BYMONTHDAY=")||r.contains("BYSETPOS=")||r.contains("BYMONTH="))return "none";if(r.contains("FREQ=DAILY"))return "daily";if(r.contains("FREQ=WEEKLY"))return "weekly";if(r.contains("FREQ=YEARLY"))return "annual";if(r.contains("FREQ=MONTHLY")){if(r.contains("INTERVAL=6"))return "semiannual";if(r.contains("INTERVAL=3"))return "quarterly";if(r.contains("INTERVAL=2"))return "bimonthly";if(r.contains("INTERVAL=")&&!r.contains("INTERVAL=1"))return "none";return "monthly";}return "none";}
    private static long parseUntil(String r,ParsedDate start,String recurrence){try{for(String part:r.split(";")){if(!part.startsWith("UNTIL="))continue;String v=part.substring(6);if(v.length()==8){LocalDate d=LocalDate.parse(v,DateTimeFormatter.BASIC_ISO_DATE);return d.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}if(v.endsWith("Z")){DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'",Locale.US);LocalDateTime dt=LocalDateTime.parse(v,f);return dt.toInstant(ZoneOffset.UTC).toEpochMilli();}}if(!"none".equals(recurrence)&&start.millis>0){int count=0;for(String part:r.split(";"))if(part.startsWith("COUNT="))count=Integer.parseInt(part.substring(6));if(count>1){ZonedDateTime z=Instant.ofEpochMilli(start.millis).atZone(ZoneId.systemDefault());int steps=count-1;switch(recurrence){case "daily":z=z.plusDays(steps);break;case "weekly":z=z.plusWeeks(steps);break;case "monthly":z=z.plusMonths(steps);break;case "bimonthly":z=z.plusMonths(2L*steps);break;case "quarterly":z=z.plusMonths(3L*steps);break;case "semiannual":z=z.plusMonths(6L*steps);break;case "annual":z=z.plusYears(steps);break;default:return 0;}return start.hasTime?z.toInstant().toEpochMilli():z.toLocalDate().atTime(23,59,59).atZone(z.getZone()).toInstant().toEpochMilli();}}}catch(Exception ignored){}return 0;}
    private static int leadMinutes(String trigger){String t=trigger.trim().toUpperCase(Locale.ROOT);if(!t.startsWith("-P"))return t.equals("PT0M")?0:-1;try{String s=t.substring(2);int days=0,hours=0,minutes=0;String datePart=s,timePart="";int ti=s.indexOf('T');if(ti>=0){datePart=s.substring(0,ti);timePart=s.substring(ti+1);}if(datePart.endsWith("D"))days=Integer.parseInt(datePart.substring(0,datePart.length()-1));if(!timePart.isEmpty()){int h=timePart.indexOf('H');if(h>=0){hours=Integer.parseInt(timePart.substring(0,h));timePart=timePart.substring(h+1);}int m=timePart.indexOf('M');if(m>=0)minutes=Integer.parseInt(timePart.substring(0,m));}return days*1440+hours*60+minutes;}catch(Exception e){return -1;}}
    private static final class ParsedDate{final long millis;final boolean hasTime;ParsedDate(long m,boolean h){millis=m;hasTime=h;}}
}
