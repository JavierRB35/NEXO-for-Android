from pathlib import Path
import subprocess, tempfile, textwrap

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')

gmail=read('app/src/main/java/com/jruiz/nexo/input/GmailWatchEngine.java')
flt=read('app/src/main/java/com/jruiz/nexo/communications/NotificationCandidateFilter.java')
temporal=read('app/src/main/java/com/jruiz/nexo/core/TemporalParser.java')
semantic=read('app/src/main/java/com/jruiz/nexo/core/EventSemanticParser.java')
listener=read('app/src/main/java/com/jruiz/nexo/communications/NexoNotificationListener.java')
db=read('app/src/main/java/com/jruiz/nexo/data/DatabaseHelper.java')
main=read('app/src/main/java/com/jruiz/nexo/MainActivity.java')
js=read('app/src/main/assets/app.js')

# Gmail v7: la API directa y el respaldo por NotificationListener comparten filtro.
assert 'gmail-filter-v7:' in gmail
assert 'shouldCaptureGmail(text, classification)' in gmail
assert 'NotificationCandidateFilter.shouldCaptureGmail(payload.text, classification)' in listener
assert 'GMAIL_FILTER_REVISION = "gmail-filter-v7"' in flt
assert 'GMAIL_ACTION_TERMS' in flt
assert 'shouldCaptureGmail' in flt

# La fecha no se vuelve a interpretar con una regex independiente en el filtro.
assert 'DATE_PATTERN' not in flt
assert 'TemporalParser.parse(raw)' in flt
assert 'isTechnicalNumericDate' in temporal
assert 'TemporalParser.isTechnicalNumericDate' in semantic

# Primera conexión = baseline puro; no hay replay automático de los últimos 7 días.
assert 'RECOVERY_QUERY' not in gmail
assert 'recoverRecentIgnored' not in gmail
baseline=gmail[gmail.index('public static void initializeBaseline'):gmail.index('public static void scan')]
assert 'processMessage(' not in baseline
assert 'rememberExternalInput("gmail", id, "gmail-baseline:" + id, 0L)' in baseline
scan=gmail[gmail.index('public static void scan'):gmail.index('private static List<String> listMessageIds')]
assert 'gmail-error:' in gmail
assert 'fingerprint.startsWith("gmail-error:")' in scan

# Explicabilidad.
for token in ['candidateReason','candidateEvidence','filterRevision']:
    assert token in flt and token in js
assert 'Por qué entró:' in js
assert 'Regla ${esc(filterRevision)}' in js

# Limpieza selectiva de GitHub + deshacer; no borra ni reabre external_watch_items.
for token in ['getTechnicalGithubGmailPendingCaptureCount','discardTechnicalGithubGmailCaptures','restoreInboxCaptures']:
    assert token in db and token in main and token in js
cleanup=db[db.index('public JSONArray discardTechnicalGithubGmailCaptures'):db.index('public int restoreInboxCaptures')]
assert 'external_watch_items' not in cleanup
assert 'status", "discarded' in cleanup
assert 'notifications@github.com' in db
assert 'No se borrará ningún correo de Gmail.' in js
assert "'Deshacer'" in js

# Compartir explícitamente con Nexo permanece fuera del filtro automático.
assert 'shouldCaptureGmail' not in main

# Ejecuta el parser de producción contra los casos críticos.
with tempfile.TemporaryDirectory() as td:
    td=Path(td)
    harness=td/'TemporalV7Harness.java'
    harness.write_text(textwrap.dedent('''
        import com.jruiz.nexo.core.TemporalParser;
        import java.time.LocalDateTime;

        public class TemporalV7Harness {
            static void check(boolean ok,String msg){ if(!ok) throw new RuntimeException(msg); }
            public static void main(String[] args){
                LocalDateTime now=LocalDateTime.of(2026,10,2,12,0);

                TemporalParser.Result a=TemporalParser.parse("Run failed — fix/3.7.4-transcription-settings",now);
                check(!a.hasDate,"3.7.4 no puede ser fecha");

                TemporalParser.Result b=TemporalParser.parse("Release v3.7.4 publicada",now);
                check(!b.hasDate,"v3.7.4 no puede ser fecha");

                TemporalParser.Result c=TemporalParser.parse("Consulta el 03.07.2027",now);
                check(c.hasDate && "2027-07-03".equals(c.dateIso()),"03.07.2027 debe seguir siendo fecha");

                TemporalParser.Result d=TemporalParser.parse("Cita de digestivo el 30/10 a las 11:00",now);
                check(d.hasDate && "2026-10-30".equals(d.dateIso()),"30/10 debe ser fecha");
                check(d.hasTime && "11:00".equals(d.timeIso()),"11:00 debe ser hora");

                TemporalParser.Result e=TemporalParser.parse("Tu póliza vence el 30/11",now);
                check(e.hasDate && "2026-11-30".equals(e.dateIso()),"30/11 debe ser fecha");

                TemporalParser.Result f=TemporalParser.parse("Run failed — 02/10/2026 20:26",now);
                check(f.hasDate && "2026-10-02".equals(f.dateIso()),"fecha técnica real debe seguir parseándose");
            }
        }
    '''),encoding='utf-8')
    classes=td/'classes'; classes.mkdir()
    subprocess.run([
        'javac','-encoding','UTF-8','-d',str(classes),
        str(root/'app/src/main/java/com/jruiz/nexo/core/TemporalParser.java'),
        str(root/'app/src/main/java/com/jruiz/nexo/core/EventSemanticParser.java'),
        str(harness)
    ],check=True)
    subprocess.run(['java','-cp',str(classes),'TemporalV7Harness'],check=True)

print('Gmail v7: contexto, baseline, fechas, limpieza y explicación validados')