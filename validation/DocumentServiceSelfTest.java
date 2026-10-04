import android.content.Context;
import com.jruiz.nexo.core.*;
import com.jruiz.nexo.documents.DocumentFiles;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.reminders.ReminderScheduler;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real file I/O and production domain code; SQLite and Android scheduling use substitutes. */
public final class DocumentServiceSelfTest {
    private static int passed;
    private static File root;
    private interface Case { void run() throws Exception; }
    private static void check(boolean yes,String message) { if(!yes)throw new AssertionError(message); }
    private static void test(String name,Case fn) throws Exception { ReminderScheduler.reset();fn.run();passed++;System.out.println("OK "+name); }
    private static Context context() { return new Context().withFilesDir(root); }
    private static File attachment(String name) throws Exception { return DocumentFiles.copy(new File(root,"attachments"),name,new ByteArrayInputStream(new byte[]{0,1,2,3,4,(byte)255})); }
    private static JSONObject draft(File file) { return new JSONObject().put("title","Póliza del coche").put("description","Cobertura completa y condiciones.").put("label","Seguro").put("attachmentPath",file.getPath()).put("attachmentName","Póliza.pdf").put("attachmentMime","application/pdf"); }
    public static void main(String[] args) throws Exception {
        root=Files.createTempDirectory("nexo-document-tests-").toFile();
        try {
            test("Documents persist only description, label and file semantics even when text contains dates",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);
                JSONObject out=service.saveDocument(draft(attachment("p.pdf")).put("description","Revisión mañana a las 20, avísame").put("dueAt",99999999).put("recurrence","weekly").put("voiceEnabled",true));
                JSONObject item=db.getItem(out.optLong("id"));check(out.optBoolean("ok"),out.toString());check("document".equals(item.optString("type"))&&item.optLong("dueAt")==0&&"none".equals(item.optString("recurrence")),"Calendar semantics leaked");check(!item.optBoolean("voiceEnabled")&&db.reminders.isEmpty(),"Alarm created");
            });
            test("Full multiline descriptions and Unicode survive the internal title generation",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);String description="📄".repeat(130)+"\nPóliza y último detalle";
                long id=service.saveDocument(draft(attachment("p.pdf")).put("description",description)).optLong("id");JSONObject i=db.getItem(id);
                check(description.equals(i.optString("body")),"Description changed");check("Póliza del coche".equals(i.optString("title")),"Explicit title changed");
            });
            test("A title and an attachment are required while description stays optional",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File f=attachment("p.pdf");
                check(service.saveDocument(draft(f).put("description"," ")).optBoolean("ok"),"Optional description rejected");
                check(!service.saveDocument(draft(f).put("title"," ").put("description"," ").put("attachmentName","")).optBoolean("ok"),"Blank title accepted");
                check(!service.saveDocument(draft(f).put("attachmentPath","")).optBoolean("ok"),"Missing file accepted");
                check(!service.saveDocument(draft(f).put("attachmentPath",new File(root,"absent").getPath())).optBoolean("ok"),"Missing file accepted");
            });
            test("Document expiry is stored without calendar or alarm semantics",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File f=attachment("expiry.pdf");long expiry=1893456000000L;
                long id=service.saveDocument(draft(f).put("expiryAt",expiry)).optLong("id");JSONObject item=db.getItem(id);
                check(item.optLong("dueAt")==expiry&&"document".equals(item.optString("type")),"Expiry not stored");
                check("none".equals(item.optString("recurrence"))&&!item.optBoolean("hasTime")&&!item.optBoolean("voiceEnabled"),"Calendar/alarm semantics leaked");
            });
            test("Blank and legacy labels can be saved and cleared",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File f=attachment("p.pdf");
                long id=service.saveDocument(draft(f).put("label","Mi etiqueta antigua")).optLong("id");check(db.getItem(id).optString("subcategory").equals("Mi etiqueta antigua"),"Old label lost");
                check(service.saveDocument(draft(f).put("id",id).put("label","")).optBoolean("ok"),"Clear failed");check(db.getItem(id).optString("subcategory").isEmpty(),"Empty label not kept");
            });
            test("Old distinct titles and descriptions remain together without duplicating new descriptions",()->{
                JSONObject i=new JSONObject().put("title","Seguro del coche").put("body","Coberturas\nCondiciones");check(DocumentEntry.description(i).equals("Seguro del coche\n\nCoberturas\nCondiciones"),"Legacy title lost");i.put("body","");check(DocumentEntry.description(i).equals("Seguro del coche"),"Title-only record lost");i.put("body","Seguro del coche y sus coberturas");check(DocumentEntry.description(i).equals(i.optString("body")),"Title duplicated");
            });
            test("Updating metadata preserves identity, attachment bytes, original capture and other metadata",()->{
                DatabaseHelper db=new DatabaseHelper();File f=attachment("p.pdf");long id=db.insertItem(new JSONObject().put("type","document").put("title","Original").put("body","Detalle").put("attachmentPath",f.getPath()).put("attachmentName","p.pdf").put("attachmentMime","application/pdf").put("tags","archivo").put("originalText","Texto capturado").put("createdAt",123).put("dueAt",456));
                NexoService service=new NexoService(context(),db);JSONObject out=service.saveDocument(new JSONObject().put("id",id).put("description","Descripción completa actualizada"));JSONObject i=db.getItem(id);
                check(out.optBoolean("ok")&&db.items.size()==1&&f.exists(),"Identity/file changed");check("Texto capturado".equals(i.optString("originalText"))&&i.optLong("createdAt")==123&&"archivo".equals(i.optString("tags")),"Old metadata lost");check(i.optLong("dueAt")==456,"Existing document date/expiry was not preserved");
            });
            test("An attachment replacement updates all file metadata and then removes the unused former copy",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File old=attachment("old.pdf"),next=attachment("new.jpg");long id=service.saveDocument(draft(old)).optLong("id");
                check(service.saveDocument(draft(next).put("id",id).put("attachmentName","Foto.jpg").put("attachmentMime","image/jpeg")).optBoolean("ok"),"Replace failed");JSONObject i=db.getItem(id);check(i.optString("attachmentPath").equals(next.getPath())&&i.optString("attachmentMime").equals("image/jpeg")&&i.optString("attachmentName").equals("Foto.jpg"),"Metadata mismatch");check(!old.exists()&&next.exists(),"Wrong file removed");
            });
            test("A failed replacement keeps the old record and both files available for retry",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File old=attachment("old.pdf"),next=attachment("new.jpg");long id=service.saveDocument(draft(old)).optLong("id");String before=db.getItem(id).toString();db.failUpdate=true;
                check(!service.saveDocument(draft(next).put("id",id)).optBoolean("ok"),"Failure ignored");check(old.exists()&&next.exists()&&db.getItem(id).toString().equals(before),"Failed save destroyed data");
            });
            test("Cancelling a draft removes only its unreferenced internal copy",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File stored=attachment("stored.pdf"),draft=attachment("draft.pdf");service.saveDocument(draft(stored));service.discardUnusedAttachment(draft.getPath());service.discardUnusedAttachment(stored.getPath());check(!draft.exists()&&stored.exists(),"Saved file removed on cancel");
            });
            test("Replacing a file preserves a copy still referenced by another item, including Undo records",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File shared=attachment("shared.pdf"),next=attachment("next.jpg");long id=service.saveDocument(draft(shared)).optLong("id");db.insertItem(new JSONObject().put("type","event").put("attachmentPath",shared.getPath()).put("deletedAt",123));service.saveDocument(draft(next).put("id",id));check(shared.exists(),"Shared/Undo file destroyed");
            });
            test("The document editor cannot overwrite a Cita or place that happens to have an attachment",()->{
                DatabaseHelper db=new DatabaseHelper();NexoService service=new NexoService(context(),db);File f=attachment("p.pdf");
                for(String type:new String[]{"event","place","note"}) {long id=db.insertItem(new JSONObject().put("type",type).put("attachmentPath",f.getPath()).put("title","Original"));check(!service.saveDocument(draft(f).put("id",id)).optBoolean("ok"),"Other item overwritten");check(db.getItem(id).optString("type").equals(type),"Type changed");}
                check(!service.saveDocument(draft(f).put("id",999)).optBoolean("ok"),"Missing record accepted");
            });
            test("Old document alarms stop while appointment alarms, dates and stored rules remain",()->{
                DatabaseHelper db=new DatabaseHelper();long doc=db.insertItem(new JSONObject().put("type","document").put("dueAt",987)),event=db.insertItem(new JSONObject().put("type","event"));long rd=db.insertReminderMinutes(doc,"Documento",999999,0,999999),re=db.insertReminderMinutes(event,"Cita",999999,0,999999);new NexoService(context(),db);check(ReminderScheduler.cancelled.contains(rd)&&!ReminderScheduler.cancelled.contains(re),"Wrong alarm cancelled");check(db.reminders.size()==2&&db.getItem(doc).optLong("dueAt")==987,"History destroyed at startup");
            });
            test("Any file format is copied byte-for-byte and equal names never overwrite each other",()->{
                byte[] bytes=new byte[65539];new Random(42).nextBytes(bytes);Set<String> paths=new HashSet<>();
                for(String name:new String[]{"archivo.pdf","archivo.jpg","archivo.docx","archivo.zip","sin extension","archivo.pdf"}) {File f=DocumentFiles.copy(new File(root,"attachments"),name,new ByteArrayInputStream(bytes));check(Arrays.equals(bytes,Files.readAllBytes(f.toPath())),"File changed");check(paths.add(f.getPath()),"Name collision");}
            });
            test("Failed reads remove incomplete internal copies",()->{
                File dir=new File(root,"broken-copy");InputStream broken=new InputStream(){int n;public int read() throws IOException{if(n++<5)return 7;throw new IOException("Read failed");}};
                boolean rejected=false;try{DocumentFiles.copy(dir,"broken.pdf",broken);}catch(IOException e){rejected=true;}check(rejected&&dir.list().length==0,"Partial file left behind");
            });
            test("File access and cleanup reject traversal, external paths and symlinks outside attachments",()->{
                File external=new File(root,"private.db");Files.write(external.toPath(),new byte[]{42});File dir=new File(root,"attachments");File link=new File(dir,"link.pdf");Files.createSymbolicLink(link.toPath(),external.toPath());
                for(String path:new String[]{external.getPath(),new File(dir,"../private.db").getPath(),link.getPath(),"file://"+external.getPath()}) {boolean rejected=false;try{DocumentFiles.requireStored(dir,path);}catch(IOException e){rejected=true;}check(rejected,"Unsafe path accepted");}
                new NexoService(context(),new DatabaseHelper()).discardUnusedAttachment(external.getPath());check(external.exists(),"External file removed");
            });
            test("Long Unicode filenames keep their extension and never become path components",()->{
                String name=DocumentFiles.safeName("📄".repeat(100)+".pdf");check(name.endsWith(".pdf")&&name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=180,"Invalid long name");check(!DocumentFiles.safeName("../../a\\b\n.pdf").contains("/"),"Path separator retained");
            });
            System.out.println("Documents native: "+passed+" scenarios passed");
        } finally {
            try(var paths=Files.walk(root.toPath())) {for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
}
