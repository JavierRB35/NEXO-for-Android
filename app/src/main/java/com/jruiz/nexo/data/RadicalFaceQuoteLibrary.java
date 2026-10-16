package com.jruiz.nexo.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Reflexiones originales en castellano inspiradas en canciones de Radical Face.
 * NO son versos literales ni traducciones textuales de letras protegidas.
 * Se cargan en instalaciones nuevas y una sola vez al migrar al esquema 26.
 */
public final class RadicalFaceQuoteLibrary {
    private static final String SOURCE_PREFIX = "seed-journal-radical-face-v26-";
    private static final String[][] SEEDS = new String[][]{
            {"Welcome Home", "Regresar no consiste en recuperar el pasado, sino en aprender a visitar el lugar que un día nos perteneció.", "Ghost", "Hogar y cambio"},
            {"Let the River In", "Romper con una herencia dolorosa exige elegir qué parte de la historia seguirá viviendo en nosotros.", "Ghost", "Herencia y libertad"},
            {"Glory", "Hay decisiones que salvan nuestra conciencia aunque nos condenen ante los demás.", "Ghost", "Conciencia y sacrificio"},
            {"The Strangest Things", "Cuando la memoria desordena el mundo, el afecto todavía puede ofrecernos un lugar reconocible.", "Ghost", "Memoria y amor"},
            {"Along the Road", "Los ausentes regresan a veces en sueños, no para quedarse, sino para recordarnos cuánto significaron.", "Ghost", "Duelo y recuerdo"},
            {"A Pound of Flesh", "La culpa pesa más cuando sabemos que el daño causado ya no tiene reparación.", "The Family Tree: The Roots", "Culpa"},
            {"Family Portrait", "Nacemos dentro de historias que empezaron mucho antes de que pudiéramos elegir.", "The Family Tree: The Roots", "Familia y destino"},
            {"Severus and Stone", "Aprender a vivir después de una pérdida no significa abandonar a quien se fue.", "The Family Tree: The Roots", "Duelo"},
            {"The Moon is Down", "A veces amar es aceptar la distancia que permite al otro seguir siendo libre.", "The Family Tree: The Roots", "Amor y renuncia"},
            {"Ghost Towns", "Se puede cruzar medio mundo y continuar prisionero del único lugar al que no se puede volver.", "The Family Tree: The Roots", "Exilio y arrepentimiento"},
            {"Always Gold", "Querer a alguien es reconocer su valor incluso cuando sus errores lo vuelven invisible para los demás.", "The Family Tree: The Roots", "Amor fraternal"},
            {"Holy Branches", "La culpa nos ata más tiempo que la pérdida, hasta que comprendemos que seguir adelante también puede ser amor.", "The Family Tree: The Branches", "Culpa y aceptación"},
            {"The Mute", "No poder expresar el propio mundo no significa que ese mundo no exista.", "The Family Tree: The Branches", "Incomprensión"},
            {"The Crooked Kind", "Lo que antes nos avergonzaba de nuestra historia puede convertirse en una forma de pertenencia.", "The Family Tree: The Branches", "Identidad y aceptación"},
            {"Chains", "Hay errores cuya verdadera condena es entender demasiado tarde quién estuvo siempre a nuestro lado.", "The Family Tree: The Branches", "Arrepentimiento y gratitud"},
            {"Southern Snow", "Una familia puede convertir lo que otros temen en algo digno de asombro.", "The Family Tree: The Branches", "Familia y diferencia"},
            {"Sisters", "Cuando el hogar deja de proteger, una mano cercana puede convertirse en el único refugio.", "The Family Tree: The Bastards", "Protección y vínculos"},
            {"Baptisms", "A veces buscamos respuestas sagradas cuando lo que realmente necesitamos es compañía.", "The Family Tree: The Bastards", "Fe y consuelo"},
            {"We're On Our Way", "No hace falta conocer el destino para permitir que la vida vuelva a moverse.", "The Family Tree: The Bastards", "Cambio y esperanza"},
            {"Small Hands", "No siempre podemos reparar el dolor ajeno, pero sí impedir que se afronte en soledad.", "The Family Tree: The Bastards", "Cuidado y solidaridad"},
            {"Nightclothes", "La muerte se lleva la presencia, pero no consigue borrar el idioma secreto de la infancia compartida.", "The Family Tree: The Bastards", "Duelo y hermanos"},
            {"Secrets (Cellar Door)", "La vergüenza pierde poder cuando descubrimos a alguien capaz de mirar nuestro secreto sin miedo.", "The Family Tree: The Leaves", "Confianza y vulnerabilidad"},
            {"Rivers in the Dust", "Dos personas pueden empezar huyendo del dolor y encontrar, sin buscarlo, una vida nueva.", "The Family Tree: The Leaves", "Resiliencia y amor"},
            {"Everything Costs", "Decir la verdad sobre el daño sufrido también exige renunciar a la comodidad del silencio.", "The Family Tree: The Leaves", "Verdad y trauma"},
            {"The Ship in Port", "La seguridad deja de proteger cuando nos obliga a renunciar a nuestra propia vida.", "The Family Tree: The Leaves", "Libertad y valentía"},
            {"Old Gemini", "Las palabras que dejamos atrás pueden abrir caminos que nunca llegaremos a recorrer.", "The Family Tree: The Leaves", "Legado y memoria"},
            {"Bad Blood", "El origen explica algunas heridas, pero no tiene derecho a decidir en quién nos convertimos.", "The Family Tree: The Leaves", "Origen e identidad"},
            {"Sunn", "Interrumpir una herencia de dolor también puede ser una manera silenciosa de cuidar el futuro.", "SunnMoonnEclippse", "Ruptura de patrones"},
            {"Moon", "La claridad llega cuando dejamos de confundir lo que nos enseñaron a ser con quienes somos.", "SunnMoonnEclippse", "Autoconocimiento"},
            {"Eclippse", "Crecer exige aceptar que no podemos rescatarlo todo, ni siquiera aquello que amamos.", "SunnMoonnEclippse", "Aceptación y pérdida"},
            {"Doubt", "Aceptar la incertidumbre puede ser el primer pensamiento propio después de años de respuestas prestadas.", "Therapy", "Duda y libertad"},
            {"Hard of Hearing", "Es posible seguir reconstruyéndose incluso en los días en que sentirse bien parece demasiado pedir.", "Therapy", "Salud emocional y resiliencia"},
            {"Personal Giants", "Las personas que más cambian una vida no siempre llegan a saber cuánto significaron.", "Therapy", "Gratitud y vínculos"},
            {"The Missing Road", "Las vidas que no elegimos siguen acompañándonos como preguntas que nunca terminan de cerrarse.", "Hidden Hollow, Vol. One", "Decisiones y nostalgia"},
            {"Sunlight", "La tristeza también altera el paisaje interior y hace parecer definitivo lo que quizá sea pasajero.", "Hidden Hollow, Vol. One", "Percepción y tristeza"},
            {"More Clay Than Stone (Still On Our Way)", "Recordar no es conservar una fotografía intacta, sino volver a moldear lo que creemos haber vivido.", "Hidden Hollow, Vol. One", "Memoria y transformación"},
            {"Lovesong", "En la inmensidad del tiempo, que dos personas se encuentren sigue siendo un acontecimiento extraordinario.", "Mixtape", "Amor y tiempo"},
            {"Teenage Feelings", "Liberarse de alguien también significa aceptar que ya no tendrá acceso a la persona en que te conviertas.", "Mixtape", "Ruptura y autonomía"},
            {"From Time to Time", "Algunas personas solo regresan durante el sueño, donde el tiempo todavía nos permite encontrarlas.", "Mixtape #2", "Sueños y memoria"},
            {"Aftermath", "La nostalgia se convierte en encierro cuando idealizamos ayer para no enfrentarnos a lo que aún podemos cambiar.", "Mixtape #2", "Nostalgia y cambio"},
    };

    private RadicalFaceQuoteLibrary() {}

    public static void seed(SQLiteDatabase db) {
        long base = System.currentTimeMillis();
        for (int i = 0; i < SEEDS.length; i++) {
            String[] entry = SEEDS[i];
            String source = SOURCE_PREFIX + i;
            // Idempotencia por identificador y por cuerpo; respeta frases importadas por el usuario.
            try (Cursor existing = db.rawQuery(
                    "SELECT id FROM journal_entries WHERE source=? OR (kind='quote' AND body=?) LIMIT 1",
                    new String[]{source, entry[1]})) {
                if (existing.moveToFirst()) continue;
            }
            ContentValues v = new ContentValues();
            v.put("kind", "quote");
            v.put("title", entry[0] + " · Radical Face");
            v.put("body", entry[1]);
            v.put("summary", "Inspirada en: «" + entry[0] + "» — Radical Face (" + entry[2] + "). Paráfrasis original; no es una cita literal.");
            v.put("tags", "Radical Face · " + entry[2] + " · " + entry[3]);
            v.put("source", source);
            v.put("favorite", 0);
            v.put("revisit", 0);
            long created = base + (i * 1000L);
            v.put("created_at", created);
            v.put("updated_at", created);
            db.insertOrThrow("journal_entries", null, v);
        }
    }
}
