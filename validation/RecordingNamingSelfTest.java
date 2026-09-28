import com.jruiz.nexo.recordings.RecordingNaming;
import java.time.ZoneId;

public final class RecordingNamingSelfTest {
    private static void check(boolean ok, String msg) { if(!ok) throw new AssertionError(msg); }
    public static void main(String[] args) {
        long ts = 1788883500000L; // fecha fija; solo importa consistencia de formato/id.
        String id = RecordingNaming.idFor(ts);
        check(id.equals("rec_1788883500000"), "id");
        check(RecordingNaming.isValidId(id), "valid id");
        check(!RecordingNaming.isValidId("../rec_1788883500000"), "path traversal");
        check(RecordingNaming.parseStartedAt(id)==ts, "parse timestamp");
        String title = RecordingNaming.defaultTitle(ts, ZoneId.of("Europe/Madrid"));
        check(title.matches("\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}"), "title format");
        String exported = RecordingNaming.exportFileName("Consulta: Dr/García?", ts);
        check(exported.equals("Consulta Dr García.m4a"), "safe export filename");
        check(RecordingNaming.exportFileName("   ", ts).endsWith(".m4a"), "fallback export filename");
        boolean failed=false;try{RecordingNaming.parseStartedAt("rec_x");}catch(IllegalArgumentException e){failed=true;}
        check(failed,"invalid id rejected");
        System.out.println("RecordingNaming OK");
    }
}
