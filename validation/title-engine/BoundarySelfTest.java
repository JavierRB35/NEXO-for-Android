import com.jruiz.nexo.core.title.AppointmentTitleEngine;
public final class BoundarySelfTest {
    private static int checks;
    private static AppointmentTitleEngine.Selection derive(String raw) {
        return AppointmentTitleEngine.derive(raw,"Nota rápida","Otro","");
    }
    private static void exact(String raw,String expected) {
        var actual=derive(raw);
        if(!expected.equals(actual.title()) || actual.needsReview())
            throw new AssertionError(raw+" => "+actual);
        checks++;
    }
    public static void main(String[] args) {
        exact("No renovar la suscripción mañana por la tarde a las 18:30 en Casa.","No renovar la suscripción");
        exact("No cancelar la reserva el 17/12/2026 a las 10:00; avísame una hora antes.","No cancelar la reserva");
        exact("No borrar el documento el viernes 18 de diciembre de 2026 a las diez y media.","No borrar el documento");
        exact("Tengo que cancelar la visita mañana a las 09:00.","Cancelar la visita");
        exact("Tengo que fotografiar el contador para reclamar la factura mañana por la tarde a las 18:30.","Fotografiar el contador para reclamar la factura");
        exact("Necesito no borrar el documento mañana a las 11:00.","No borrar el documento");
        String longClause="No borrar los documentos originales de las reparaciones de la vivienda ni las fotografías de los daños de la habitación";
        var longResult=derive(longClause+" mañana a las 10:00.");
        if(!longResult.needsReview() || !longResult.title().equals(longClause))
            throw new AssertionError("Se truncó silenciosamente un contenido largo: "+longResult);
        checks++;
        System.out.println("BoundarySelfTest OK: "+checks);
    }
}
