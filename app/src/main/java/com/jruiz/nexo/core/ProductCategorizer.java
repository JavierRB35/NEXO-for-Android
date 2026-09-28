package com.jruiz.nexo.core;

import java.text.Normalizer;
import java.util.Locale;

public final class ProductCategorizer {
    private ProductCategorizer() {}

    public static String category(String item) {
        String s = norm(item);
        if (has(s,"leche","yogur","queso","mantequilla","nata")) return "Lácteos";
        if (has(s,"huevo","huevos")) return "Huevos";
        if (has(s,"agua","vino","cerveza","refresco","zumo","cafe","te ","coca cola","coca-cola","cocacola","pepsi")) return "Bebidas";
        if (has(s,"jamon","pollo","carne","ternera","cerdo","pavo","embutido")) return "Carnes y charcutería";
        if (has(s,"pescado","salmon","atun","merluza","gamba")) return "Pescado";
        if (has(s,"manzana","pera","platano","naranja","tomate","lechuga","patata","cebolla","fruta","verdura")) return "Fruta y verdura";
        if (has(s,"detergente","lejia","suavizante","lavavajillas","limpiador","bayeta","papel cocina")) return "Limpieza";
        if (has(s,"pan","galleta","cereal","arroz","pasta","macarron","espagueti","fideo","harina","azucar","aceite","sal","legumbre")) return "Despensa";
        if (has(s,"champu","gel","pasta dientes","desodorante","papel higienico")) return "Higiene";
        return "Otros";
    }

    private static boolean has(String s, String... words) { for (String w: words) if (s.contains(w)) return true; return false; }
    private static String norm(String s) {
        return Normalizer.normalize((s == null ? "" : s).toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
