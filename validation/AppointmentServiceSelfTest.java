import android.content.Context;
import com.jruiz.nexo.core.*;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;
import org.json.*;

/** Exercises production parser and service. DB and Android scheduling are fakes. */
public final class AppointmentServiceSelfTest {
    private static int passed=0;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static void scenario(String name,Runnable test) { ReminderScheduler.reset();test.run();passed++;System.out.println("OK "+name); }
    private static JSONObject draft(String text) { return new JSONObject().put("appointmentCapture",true).put("originalText",text); }
    public static void main(String[] args) {
        String[] phrases={"Recuérdame llamar a Ana","Tengo una tarea para mañana","Cita con el dentista mañana a las diez","Comprar leche","Renovar la póliza del coche","Guardar contrato de alquiler","Pagar el recibo del banco","Llamar a casa","Organizar los papeles del cajón","Pasar por la farmacia","Pon una alarma a las ocho","Cena en restaurante La Terraza","Reunión el 22 de abril de 2030 a las 16:00","Cumpleaños de Ana el 18 de abril","Comprar paracetamol","Tengo que preparar el viaje","Anota una nota sobre la reserva","Revisar la caldera","Documentos de la ITV","Enviar el informe firmado por la doctora y la autorización de la aseguradora","Lista de la compra","Llevar medicación al hospital","Recordatorio sin fecha",""};
        scenario("General capture fixes the type for 24 different phrases",()->{
            for(String text:phrases){RuleClassifier.Result r=AppointmentParser.parse(text);check("event".equals(r.type),text);check(!r.needsReview,"Unexpected classification review "+text);check(!"Tareas".equals(r.category)&&!"Recordatorios".equals(r.category),"Legacy category "+text);check(text.isEmpty()||!r.description.isBlank(),"Missing description "+text);}
            check("event".equals(AppointmentParser.parse(null).type),"Null capture");
        });
        scenario("Appointment suggestions preserve date time location recurrence and alert extraction",()->{
            for(String text:new String[]{"Dentista el 22 de abril de 2030 a las 16:00 en Clínica Norte, avísame una hora antes","Cumpleaños de Ana el 18 de abril","Revisión todos los lunes a las diez, avísame por voz"}){
                RuleClassifier.Result old=RuleClassifier.classify(text),r=AppointmentParser.parse(text);
                check(old.dueAt==r.dueAt&&old.hasTime==r.hasTime,"Date or time changed");check(old.locationText.equals(r.locationText),"Location lost");check(old.recurrence.equals(r.recurrence),"Recurrence lost");check(old.reminderLeadMinutes.equals(r.reminderLeadMinutes)&&old.voiceRequested==r.voiceRequested,"Alerts lost");
            }
        });
        scenario("No invented date for an undated task",()->{RuleClassifier.Result r=AppointmentParser.parse("Organizar los papeles del cajón");check(r.dueAt==0&&!r.hasTime,"Invented date");});
        scenario("Learned categories cannot restore the old Task or Reminder identity",()->{
            DatabaseHelper db=new DatabaseHelper();db.learned="Tareas";NexoService service=new NexoService(new Context(),db);JSONObject result=service.classifyAppointment("Llamar a Ana");check("event".equals(result.optString("type"))&&!result.optBoolean("needsReview"),"Type changed");check("Personal".equals(result.optString("category")),"Old category restored");
        });
        scenario("Explicitly cleared fields win over the original dictation",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);JSONObject p=draft("Cita mañana a las diez en Clínica Norte").put("title","Título corregido").put("body","").put("dueAt",0).put("locationText","").put("recurrence","none");JSONObject result=service.saveCapture(p);check(result.optBoolean("ok"),"Save failed");JSONObject i=db.getItem(result.optLong("id"));check(i.optLong("dueAt")==0&&!i.optBoolean("hasTime"),"Date restored");check(i.optString("body").isEmpty()&&i.optString("locationText").isEmpty(),"Cleared text restored");check("event".equals(i.optString("type"))&&"pending".equals(i.optString("status")),"Wrong canonical identity");
        });
        scenario("Missing optional fields still receive parser suggestions",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);String text="Dentista el 22 de abril de 2030 a las 16:00 en Clínica Norte";RuleClassifier.Result r=AppointmentParser.parse(text);JSONObject out=service.saveCapture(draft(text));JSONObject item=db.getItem(out.optLong("id"));check(out.optBoolean("ok"),"Save failed");check(item.optLong("dueAt")==r.dueAt&&item.optString("locationText").equals(r.locationText),"Suggestions missing");
        });
        scenario("Manual title-only Cita is saved without an alert or a date",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);JSONObject out=service.saveCapture(draft("").put("title","Preparar papeles").put("dueAt",0));check(out.optBoolean("ok"),"Manual save failed");check(db.getItem(out.optLong("id")).optLong("dueAt")==0&&db.reminders.isEmpty(),"Invented date or alarm");
        });
        scenario("Completing and reopening preserves two alert rules and their ids",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);long future=System.currentTimeMillis()+10*86400000L;long id=service.saveCapture(draft("Revisión").put("title","Revisión").put("dueAt",future).put("hasTime",true).put("reminderLeadMinutes",new JSONArray().put(60).put(1440))).optLong("id");check(ReminderScheduler.scheduled.size()==2,"Alerts not scheduled");String ids=db.reminders.keySet().toString();
            check(service.updateItem(new JSONObject().put("id",id).put("status","done")).optBoolean("ok"),"Complete failed");check(ReminderScheduler.scheduled.isEmpty()&&db.getActiveRemindersForItem(id).length()==0,"Completed Cita still alerts");check(db.reminders.size()==2,"Rules deleted");
            service.updateItem(new JSONObject().put("id",id).put("status","pending"));check(ReminderScheduler.scheduled.size()==2&&ids.equals(db.reminders.keySet().toString()),"Reopen lost rules");
        });
        scenario("Changing alerts on a completed Cita keeps them inactive until reopened",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);long id=service.saveCapture(draft("Revisión").put("dueAt",System.currentTimeMillis()+10*86400000L).put("hasTime",true).put("status","done")).optLong("id");check(service.replaceReminders(id,new JSONArray().put(30).put(60)).optBoolean("ok"),"Replace failed");check(db.reminders.size()==2&&ReminderScheduler.scheduled.isEmpty(),"Completed Cita alerts");service.updateItem(new JSONObject().put("id",id).put("status","pending"));check(ReminderScheduler.scheduled.size()==2,"Reopening lost new alerts");
        });
        scenario("Date and status update cancels old alarms and reschedules only once",()->{
            DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(new Context(),db);long future=System.currentTimeMillis()+10*86400000L;long id=service.saveCapture(draft("Revisión").put("dueAt",future).put("hasTime",true).put("reminderLeadMinutes",new JSONArray().put(60))).optLong("id");int before=db.reschedules;service.updateItem(new JSONObject().put("id",id).put("status","pending").put("dueAt",future+86400000L));check(db.reschedules==before+1,"Duplicate rescheduling");check(ReminderScheduler.cancelled.size()==1&&ReminderScheduler.scheduled.values().contains(future+86400000L-3600000L),"Old trigger retained");
            service.updateItem(new JSONObject().put("id",id).put("dueAt",0));check(ReminderScheduler.scheduled.isEmpty(),"Clearing date left alarm");
        });
        scenario("A failed update does not schedule notifications",()->{
            NexoService service=new NexoService(new Context(),new DatabaseHelper());check(!service.updateItem(new JSONObject().put("id",999).put("status","done")).optBoolean("ok"),"Missing item accepted");check(ReminderScheduler.scheduled.isEmpty()&&ReminderScheduler.cancelled.isEmpty(),"Unrelated notification changed");
        });
        System.out.println("Appointment parser/service: "+passed+" scenarios passed");
    }
}
