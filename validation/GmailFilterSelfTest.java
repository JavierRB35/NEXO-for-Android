import com.jruiz.nexo.communications.NotificationCandidateFilter;
import com.jruiz.nexo.core.RuleClassifier;
import com.jruiz.nexo.core.TemporalParser;
import org.json.JSONObject;

import java.time.LocalDateTime;

public final class GmailFilterSelfTest {
    private static void expect(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static boolean gmail(String text){
        JSONObject c=RuleClassifier.classify(text).toJson();
        return NotificationCandidateFilter.shouldCaptureGmail(text,c);
    }
    public static void main(String[] args){
        LocalDateTime now=LocalDateTime.of(2026,10,2,21,30);

        expect(!TemporalParser.parse("Run failed — fix/3.7.4-transcription-settings",now).hasDate,
                "3.7.4 dentro de una rama técnica no puede ser fecha");
        expect(!TemporalParser.parse("Release v3.7.4 publicada",now).hasDate,
                "v3.7.4 no puede ser fecha");
        expect(!TemporalParser.parse("sherpa-onnx 1.13.7",now).hasDate,
                "1.13.7 no puede ser fecha");
        expect(TemporalParser.parse("Consulta el 03.07.2027",now).hasDate,
                "03.07.2027 debe seguir siendo una fecha válida");

        expect(!gmail("Run failed — fix/3.7.4-transcription-settings"),
                "CI con versión técnica debe omitirse");
        expect(!gmail("Run failed — 02/10/2026 20:26"),
                "fecha/hora de ejecución aisladas no justifican Inbox");
        expect(!gmail("Factura emitida el 02/10/2026"),
                "una fecha informativa de factura no implica una acción");
        expect(gmail("Cita de digestivo el 30/10 a las 11:00"),
                "una cita con fecha/hora debe capturarse");
        expect(gmail("Consulta el 03.07.2027"),
                "una consulta con fecha legítima debe capturarse");
        expect(gmail("Tu póliza vence el 30/11"),
                "un vencimiento debe capturarse");
        expect(gmail("Reserva confirmada para el 12/10 a las 21:30"),
                "una reserva confirmada debe capturarse");
        expect(gmail("Recoge tu pedido mañana"),
                "una acción concreta para el usuario debe capturarse");

        JSONObject explained=RuleClassifier.classify("Tu póliza vence el 30/11").toJson();
        expect(NotificationCandidateFilter.shouldCaptureGmail("Tu póliza vence el 30/11",explained),
                "vencimiento esperado");
        expect(!explained.optString("candidateReason","").isBlank(),"debe guardar candidateReason");
        expect(!explained.optString("candidateEvidence","").isBlank(),"debe guardar candidateEvidence");
        expect("gmail-filter-v7".equals(explained.optString("filterRevision","")),"debe identificar gmail-filter-v7");

        // El filtro general conserva sensibilidad para SMS/notificaciones.
        JSONObject sms=RuleClassifier.classify("03/10/2026").toJson();
        expect(NotificationCandidateFilter.shouldCapture("03/10/2026",sms),
                "el filtro general no debe endurecerse como Gmail");

        System.out.println("Gmail filter v7: contexto, fechas y evidencia OK");
    }
}
