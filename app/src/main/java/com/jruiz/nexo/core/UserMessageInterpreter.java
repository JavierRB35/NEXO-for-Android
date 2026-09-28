package com.jruiz.nexo.core;

import org.json.JSONObject;

/**
 * Enrutador local de intención para la entrada universal por voz.
 * Mantenerlo separado de la UI permite ampliar reglas y conservar casos de regresión.
 */
public final class UserMessageInterpreter {
    private UserMessageInterpreter() {}

    public static JSONObject route(String raw) {
        JSONObject out=new JSONObject();
        String text=raw==null?"":raw.trim();
        String n=RuleClassifier.norm(text);
        try {
            out.put("text",text);out.put("intent","capture");out.put("confidence",0.55);
            if (AlarmIntentParser.looksLikeAlarm(text)) { out.put("intent","alarm");out.put("confidence",0.99);out.put("alarm",AlarmIntentParser.parse(text));return out; }
            if (isJournal(n)) { out.put("intent","journal");out.put("confidence",0.96);return out; }
            if (MedicationParser.looksLikeMedication(text)) { out.put("intent","medication");out.put("confidence",0.95);return out; }
            if (ListVoiceParser.looksLikeShoppingCommand(text)) { out.put("intent","shopping");out.put("confidence",0.94);return out; }
            if (isParking(n)) { out.put("intent","parking");out.put("confidence",0.96);return out; }
        } catch(Exception ignored){}
        return out;
    }

    private static boolean isJournal(String n){
        if(n.matches("^(?:pensamiento|reflexion|frase)\\b.*"))return true;
        if(n.matches("^(?:guarda|guardame|apunta|anota)\\s+(?:un|una)?\\s*(?:pensamiento|reflexion|frase)\\b.*"))return true;
        // "cita" sola suele significar una cita de calendario; solo se trata como Cuaderno si se expresa como cita/frase textual.
        return n.matches("^(?:guarda|guardame|apunta|anota)\\s+(?:una\\s+)?cita\\s+(?:que\\s+dice|textual|frase)\\b.*");
    }
    private static boolean isParking(String n){return n.contains("he aparcado")||n.contains("aparque")||n.contains("aparcado")||n.contains("guardar aparcamiento")||n.contains("donde he aparcado");}
}
