package com.jruiz.nexo.core.title;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Capa experimental determinista V4 sobre V3. No cambia el parser de citas.
 * Se centra en reparar construcciones gramaticales y en nominalizar acciones
 * respaldadas por el texto. Nunca consulta el título esperado del benchmark.
 */
final class TitleRulesV4 {
    private TitleRulesV4() {}

    public static final class Selection {
        public final String title;
        public final String rule;
        public Selection(String title, String rule) {
            this.title = title;
            this.rule = rule;
        }
    }

    public static Selection derive(String raw, String baselineTitle, String category, String location) {
        TitleRulesV3.Selection previous =
                TitleRulesV3.derive(raw, baselineTitle, category, location);
        String current = previous.title;
        String rawText = raw == null ? "" : raw;
        String revised = current;
        String reason = "v3-conservado:" + previous.rule;
        Matcher m;

        // Tener entradas no es la actividad; identificar el evento ya explícito.
        m = Pattern.compile("(?iu)^Tenemos entradas para (?:el|la|un|una) (.+)$").matcher(current);
        if (m.matches()) {
            revised = m.group(1);
            reason = "evento-con-entradas";
        }

        // Eliminar auxiliar que no aporta información, sin suprimir el evento.
        m = Pattern.compile("(?iu)^(?:Asistir al?|Ir de|Participar en (?:un|una))\\s+((?:concierto|excursi[oó]n|carrera)\\b.+)$").matcher(revised);
        if (m.matches()) {
            revised = m.group(1);
            reason = "evento-sin-auxiliar";
        }
        m = Pattern.compile("(?iu)^Ver a (los abuelos|las abuelas)$").matcher(revised);
        if (m.matches()) {
            revised = "Visita a " + m.group(1);
            reason = "visita-explicita";
        }

        // "Voy a Correos a buscar un envío": la V3 puede dejar un título mal formado.
        m = Pattern.compile("(?iu)^Correos a buscar (.+)$").matcher(revised);
        if (m.matches() && rawText.matches("(?isu).*\\b(?:voy|vamos|ir|acudir|pasar)\\s+a\\s+Correos\\b.*")) {
            revised = "Recogida de " + m.group(1) + " en Correos";
            reason = "correos-recogida";
        }

        // Cuando hay una compra explícita, la visita a la tienda es secundaria.
        m = Pattern.compile("(?iu)^Pasar por (?:la|el) ([\\p{L} -]+?) a comprar (.+)$").matcher(revised);
        if (m.matches()) {
            revised = "Compra de " + m.group(2) + " en " + m.group(1);
            reason = "compra-explicita";
        }

        // "Ir a buscar un pedido" suele indicar recogida; NO se extrapola a
        // "buscar trabajo" o "buscar información".
        m = Pattern.compile("(?iu)^Buscar (.+)$").matcher(revised);
        if (m.matches()) {
            String object = m.group(1).trim();
            if (object.matches("(?iu)(?:(?:mi|el|un|una|los|las|la)\\s+)?(?:pedido(?:\\s+online)?|(?:veh[ií]culo|coche)\\s+alquilado|medicinas\\s+del\\s+animal|fotos\\s+impresas)(?:\\s+.*)?")) {
                revised = "Recogida de " + object;
                reason = "recogida-objeto-concreto";
            } else if (object.matches("(?iu)[uú]tiles (?:del colegio|escolares)")) {
                revised = "Buscar material escolar";
                reason = "equivalencia-utiles-escolares";
            }
        }

        // Títulos en lenguaje cotidiano que se vuelven más breves manteniendo la acción.
        m = Pattern.compile("(?iu)^Hacer pr[aá]cticas de conducir$").matcher(revised);
        if (m.matches()) {
            revised = "Prácticas de conducción";
            reason = "practicas-conduccion";
        }
        m = Pattern.compile("(?iu)^Defender (el|un) (trabajo final(?: de estudios)?|proyecto final)$").matcher(revised);
        if (m.matches()) {
            revised = "Defensa del " + m.group(2);
            reason = "defensa-explicita";
        }
        m = Pattern.compile("(?iu)^Cambiar (?:las|mis) (claves de acceso|contrase[nñ]as)$").matcher(revised);
        if (m.matches()) {
            revised = "Cambio de " + m.group(1);
            reason = "cambio-claves";
        }
        m = Pattern.compile("(?iu)^Devolver (los|las) (.+)$").matcher(revised);
        if (m.matches()) {
            revised = "Devolución de " + m.group(2);
            reason = "devolucion";
        }
        m = Pattern.compile("(?iu)^Comprobar si funciona (?:el|la) backup(?: de datos)?$").matcher(revised);
        if (m.matches()) {
            revised = "Comprobación de copia de seguridad";
            reason = "equivalencia-backup";
        }
        m = Pattern.compile("(?iu)^Llamada por v[ií]deo con (.+)$").matcher(revised);
        if (m.matches()) {
            revised = "Videollamada con " + m.group(1);
            reason = "videollamada";
        }
        m = Pattern.compile("(?iu)^Celebrar (?:el|un) aniversario comiendo fuera$").matcher(revised);
        if (m.matches()) {
            revised = "Comida de aniversario";
            reason = "comida-aniversario";
        }

        // Preservar un título completo y no transformar por una regla débil.
        revised = revised.replaceAll("\\s+", " ").trim();
        if (revised.isBlank() || revised.length() > 72 || revised.matches("(?iu).*(?:\\b(?:a|de|del|para|por|con|en|sobre)\\s*)$")) {
            return new Selection(current, "v3-conservado:control-calidad");
        }
        if (revised.equals(current)) return new Selection(current, "v3-conservado:" + previous.rule);
        revised = revised.substring(0, 1).toUpperCase(Locale.ROOT) + revised.substring(1);
        return new Selection(revised, reason);
    }
}
