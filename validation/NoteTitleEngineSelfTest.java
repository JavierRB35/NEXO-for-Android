import com.jruiz.nexo.core.NoteTitleEngine;
import com.jruiz.nexo.core.TemporalParser;

public class NoteTitleEngineSelfTest {
    private static int total=0, ok=0;
    private static void t(String input,String... required){
        total++;
        String got=NoteTitleEngine.derive(input);
        String n=TemporalParser.norm(got);
        boolean pass=!got.isBlank() && got.length()<=80 && got.split("\\s+").length<=10;
        for(String r:required) pass &= n.contains(TemporalParser.norm(r));
        if(pass) ok++;
        else System.out.println("FAIL\n  IN : "+input+"\n  NEED: "+String.join(", ",required)+"\n  GOT: "+got);
    }
    public static void main(String[] args){
        t("He hablado con Mapfre y me dicen que para renovar la póliza tengo que enviar el recibo del año pasado.","renovar","póliza","mapfre");
        t("He hablado con Ana sobre el presupuesto de la reforma del baño.","presupuesto","baño","ana");
        t("He hablado con Carlos acerca de la reserva del restaurante.","reserva","restaurante","carlos");
        t("He hablado con el banco para revisar la hipoteca.","revisar","hipoteca","banco");
        t("Me han dicho que para renovar el pasaporte necesito llevar una fotografía.","renovar","pasaporte");
        t("Me ha dicho el fontanero que la llave está en el armario.","llave");
        t("El fontanero me ha dicho que la llave general está detrás del mueble de la cocina.","llave","general");
        t("Me explicaron que el mando de la calefacción está en el cajón del salón.","mando","calefacción");
        t("Medidas del hueco del frigorífico: 186 de alto, 61 de ancho y 65 de fondo.","medidas","frigorífico");
        t("Dimensiones de la mesa del comedor: 180 por 90.","dimensiones","mesa");
        t("El teléfono de Pedro es 600123456.","teléfono","pedro");
        t("La dirección de la clínica es calle Colón 20.","dirección","clínica");
        t("La contraseña del wifi es Casa2026.","contraseña","wifi");
        t("El código de la alarma es 4821.","código","alarma");
        t("La matrícula del coche de Ana es 1234ABC.","matrícula","coche","ana");
        t("El horario de la farmacia de guardia es de nueve a diez.","horario","farmacia");
        t("Ideas para cuando vayamos a Irlanda: mirar alquiler de coche, seguro y adaptadores.","ideas","irlanda");
        t("Ideas para reformar la cocina: cambiar encimera y pintar.","ideas","cocina");
        t("Cosas para el cumpleaños de Marta: reservar restaurante y comprar regalo.","cosas","cumpleaños","marta");
        t("Para el viaje a Roma mirar un hotel cerca de Termini.","viaje","roma");
        t("Viaje a Lisboa buscar restaurantes y transporte.","viaje","lisboa");
        t("Vacaciones en Asturias reservar alojamiento.","viaje","asturias");
        t("Tengo que enviar el contrato a Marta antes del viernes.","enviar","contrato","marta");
        t("Tenemos que llevar las llaves a Carlos mañana.","llevar","llaves","carlos");
        t("Debo revisar el seguro del coche porque vence pronto.","revisar","seguro","coche");
        t("Hay que reservar mesa en Casa Baldo.","reservar","casa","baldo");
        t("Necesito llamar al seguro para preguntar por la franquicia.","llamar","seguro");
        t("Necesitamos comprobar las medidas del dormitorio.","comprobar","medidas","dormitorio");
        t("Para renovar el carnet tengo que presentar una foto.","renovar","carnet");
        t("Para enviar la documentación necesito escanear el DNI.","enviar","documentación");
        t("La llave del trastero está detrás del cuadro del recibidor.","llave","trastero");
        t("El mando del garaje se encuentra en el segundo cajón.","mando","garaje");
        t("La documentación queda en la carpeta azul.","documentación");
        t("El coche hace un ruido al girar a la izquierda.","ruido","coche");
        t("La lavadora produce un ruido al centrifugar.","ruido","lavadora");
        t("El frigorífico tiene un ruido extraño por la noche.","ruido","frigorífico");
        t("Apunta que la contraseña del wifi es Casa2026.","contraseña","wifi");
        t("Anota que la llave del buzón está en la entrada.","llave","buzón");
        t("Quiero anotar que el coche hace un ruido al girar.","ruido","coche");
        t("Quiero recordar que la póliza vence en noviembre.","póliza","noviembre");
        t("Nota: revisar las condiciones del seguro de viaje antes de contratarlo.","revisar","seguro","viaje");
        t("Guarda que el número de referencia es ABC123.","referencia");
        t("El recibo del agua está domiciliado en la cuenta conjunta.","recibo","agua");
        t("La garantía de la televisión termina en diciembre.","garantía","televisión");
        t("El médico recomienda caminar media hora todos los días.","médico","caminar");
        t("Recordar dónde dejé los papeles del seguro.","papeles","seguro");
        t("Lista de documentos que hacen falta para la escritura.","documentos","escritura");
        t("Precio aproximado de cambiar las ventanas del salón.","precio","ventanas");
        t("Nombre del contacto de la gestoría: Laura Martínez.","nombre","gestoría");
        t("La receta de la paella lleva arroz, pollo y judías.","receta","paella");
        t("Correo de soporte de la aseguradora es ayuda arroba ejemplo punto com.","correo","aseguradora");
        t("IBAN de la cuenta de ahorro es ES001234.","iban","cuenta");
        t("Dirección del taller: avenida del Puerto 100.","dirección","taller");
        t("Código del portal es 4582.","código","portal");
        t("Ideas sobre regalos para los nietos: libros y juegos.","ideas","regalos","nietos");
        t("Escapada a Cuenca buscar un hotel céntrico.","viaje","cuenca");
        t("Tengo que recoger las gafas en la óptica.","recoger","gafas");
        t("Debemos solicitar el certificado al ayuntamiento.","solicitar","certificado");
        t("Necesito comprobar la presión de las ruedas.","comprobar","presión","ruedas");
        t("Hay que guardar una copia del contrato en la carpeta azul.","guardar","copia","contrato");

        double pct=total==0?0:(100.0*ok/total);
        System.out.printf("NoteTitleEngine semantic benchmark: %d/%d = %.2f%%%n",ok,total,pct);
        if(pct<90.0) System.exit(1);
    }
}
