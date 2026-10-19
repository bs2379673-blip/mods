package com.tunombre.anomalias;

import java.util.Locale;

/** Tipos de anomalia: color del cielo, violencia de la tormenta y nivel de peligro (el texto que sale en el aviso). */
public enum Tipo {
    ROJO("rojo", "ROJA", "EXTREMO", 1.00F, new float[]{0.16F, 0F, 0.02F}, new float[]{0.75F, 0.04F, 0.06F}, new float[]{1F, 0.45F, 0.35F}),
    AMBAR("ambar", "AMBAR", "ALTO", 0.80F, new float[]{0.14F, 0.05F, 0F}, new float[]{0.9F, 0.42F, 0.02F}, new float[]{1F, 0.85F, 0.4F}),
    VIOLETA("violeta", "VIOLETA", "MEDIO", 0.65F, new float[]{0.08F, 0F, 0.18F}, new float[]{0.45F, 0.12F, 0.85F}, new float[]{0.85F, 0.65F, 1F}),
    AZUL("azul", "AZUL", "MEDIO", 0.70F, new float[]{0F, 0.04F, 0.16F}, new float[]{0.10F, 0.35F, 0.95F}, new float[]{0.65F, 0.9F, 1F}),
    VERDE("verde", "VERDE", "BAJO", 0.50F, new float[]{0F, 0.10F, 0.04F}, new float[]{0.10F, 0.65F, 0.25F}, new float[]{0.65F, 1F, 0.6F});

    public final String id, nombre, peligro;
    public final float storm;
    public final float[] c0, c1, c2;

    Tipo(String id, String nombre, String peligro, float storm, float[] c0, float[] c1, float[] c2) {
        this.id = id; this.nombre = nombre; this.peligro = peligro; this.storm = storm; this.c0 = c0; this.c1 = c1; this.c2 = c2;
    }

    public static Tipo of(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        for (Tipo t : values()) if (t.id.equals(l)) return t;
        return switch (l) { case "naranja", "amarillo" -> AMBAR; case "morado" -> VIOLETA; default -> null; };
    }
}
