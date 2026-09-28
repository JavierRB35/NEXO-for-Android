package com.jruiz.nexo.core;

public final class LocationParserSelfTest {
    private static int tests = 0;

    public static void main(String[] args) {
        expect("Cita mañana a las 10 en Vithas Valencia.", "Vithas Valencia");
        expect("Dentista el martes a las cinco en Clínica Dental Navarro.", "Clínica Dental Navarro");
        expect("El jueves a las siete he quedado con Carlos en Nuevo Centro.", "Nuevo Centro");
        expect("Reunión en las oficinas de Sanz el jueves a las 11.", "Oficinas de Sanz");
        expect("Comer el domingo en Casa Baldo.", "Casa Baldo");
        expect("Cita en Hospital La Fe, avenida Fernando Abril Martorell.", "Hospital La Fe, avenida Fernando Abril Martorell");
        expect("Recoger paquete en Punto Pack de Ruzafa mañana.", "Punto Pack de Ruzafa");
        expect("Lugar: Nuevo Centro.", "Nuevo Centro");
        expect("Ubicación - Plaza del Ayuntamiento 1; Valencia", "Plaza del Ayuntamiento 1");
        expect("Cita mañana en el Hospital Vithas Valencia a las 10.", "Hospital Vithas Valencia");
        expect("Recordatorio de cita Centro: Hospital Vithas Valencia Dirección: Calle Vall de la Ballestera 59 Fecha: 26/08/2026 Hora: 09:30 PET-TAC.",
                "Hospital Vithas Valencia, Calle Vall de la Ballestera 59");

        expect("Recuérdame en dos días llamar a Juan.", "");
        expect("Avísame en 30 minutos.", "");
        expect("Dímelo en voz alta.", "");
        expect("Guardar en Nexo.", "");
        expect("Pienso en Juan.", "");
        expect("Cita en septiembre.", "");
        expect("Reunión en el momento que puedas.", "");

        System.out.println("OK: " + tests + " pruebas de ubicación superadas");
    }

    private static void expect(String input, String expected) {
        tests++;
        String actual = LocationParser.extract(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("Caso " + tests + "\nEntrada: " + input + "\nEsperado: [" + expected + "]\nObtenido: [" + actual + "]");
        }
    }
}
