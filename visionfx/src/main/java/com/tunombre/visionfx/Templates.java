package com.tunombre.visionfx;

import java.util.LinkedHashMap;
import java.util.Map;

/** plantillas de color listas para usar: /visionfx plantilla <nombre> (se pueden retocar despues con /visionfx set ...) */
public final class Templates {
    private Templates() { }

    public static final Map<String, Params> ALL = new LinkedHashMap<>();
    public static final Map<String, String> DESC = new LinkedHashMap<>();

    private static void add(String name, String desc, float contrast, float desat, float sat, float bright, float gamma, float chroma, float vign,
                            float amt, float[] sh, float[] hi, boolean dark) {
        Params p = new Params();
        p.strength = 1F; p.contrast = contrast; p.desaturate = desat; p.saturation = sat; p.brightness = bright; p.gamma = gamma;
        p.chromatic = chroma; p.vignette = vign; p.toneAmt = amt; p.dark = dark;
        if (sh != null) { p.shR = sh[0]; p.shG = sh[1]; p.shB = sh[2]; }
        if (hi != null) { p.hiR = hi[0]; p.hiG = hi[1]; p.hiB = hi[2]; }
        ALL.put(name, p);
        DESC.put(name, desc);
    }

    static {
        //   nombre         descripcion                                     contr desat  sat   brillo gamma chroma vign  tono  sombras                luces
        add("original",    "el filtro de siempre (estilo mod de cordura)", 0.50f, 0.20f, 1.00f, 0.95f, 1.00f, 0.000f, 0.00f, 0.00f, null, null, false);
        add("neutro",      "sin filtro de color, todo como en vanilla",    0.00f, 0.00f, 1.00f, 1.00f, 1.00f, 0.000f, 0.00f, 0.00f, null, null, false);
        add("cinematico",  "cine: sombras azul-verdoso, luces anaranjadas", 0.35f, 0.05f, 0.92f, 0.97f, 1.00f, 0.004f, 0.55f, 0.65f, new float[]{0.40f, 0.50f, 0.58f}, new float[]{0.60f, 0.50f, 0.38f}, false);
        add("vivido",      "colores intensos y brillantes",                0.35f, 0.00f, 1.40f, 1.05f, 1.05f, 0.000f, 0.10f, 0.00f, null, null, false);
        add("oscuro",      "sombrio: todo mas oscuro y con mucho contraste", 0.60f, 0.30f, 0.80f, 0.78f, 0.90f, 0.003f, 0.90f, 0.35f, new float[]{0.42f, 0.46f, 0.58f}, null, false);
        add("ambiental",   "suave y brumoso, tono frio y calmado",         0.10f, 0.15f, 0.85f, 1.02f, 1.10f, 0.000f, 0.35f, 0.50f, new float[]{0.45f, 0.52f, 0.58f}, new float[]{0.55f, 0.55f, 0.50f}, false);
        add("terror",      "Obscure: verde enfermizo, vineta y poca luz",  0.80f, 0.45f, 0.70f, 0.85f, 0.92f, 0.010f, 1.10f, 0.60f, new float[]{0.40f, 0.52f, 0.46f}, new float[]{0.56f, 0.53f, 0.44f}, true);
        add("mortifilia",  "violeta enfermo, como la flor del cielo",      0.60f, 0.20f, 1.10f, 0.88f, 0.95f, 0.012f, 0.90f, 0.70f, new float[]{0.58f, 0.40f, 0.62f}, new float[]{0.62f, 0.48f, 0.55f}, false);
        add("calido",      "atardecer: naranjas y dorados",                0.30f, 0.00f, 1.15f, 1.02f, 1.00f, 0.000f, 0.25f, 0.55f, new float[]{0.55f, 0.47f, 0.40f}, new float[]{0.62f, 0.52f, 0.38f}, false);
        add("frio",        "helado: azules y blancos",                     0.30f, 0.10f, 0.90f, 1.00f, 1.00f, 0.000f, 0.25f, 0.55f, new float[]{0.40f, 0.48f, 0.62f}, new float[]{0.46f, 0.52f, 0.60f}, false);
        add("noir",        "blanco y negro de cine negro",                 1.10f, 1.00f, 1.00f, 0.95f, 0.95f, 0.000f, 1.00f, 0.00f, null, null, false);
        add("sepia",       "foto antigua, marrones",                       0.30f, 0.70f, 1.00f, 0.98f, 1.00f, 0.000f, 0.80f, 0.85f, new float[]{0.46f, 0.42f, 0.36f}, new float[]{0.60f, 0.52f, 0.40f}, false);
        add("pesadilla",   "rojo intenso y aberracion, muy tenso",         1.00f, 0.00f, 1.40f, 0.85f, 0.90f, 0.025f, 1.20f, 0.75f, new float[]{0.62f, 0.40f, 0.40f}, new float[]{0.60f, 0.48f, 0.48f}, false);
        add("onirico",     "sueño: pasteles, brillo suave rosa y cian",    0.00f, 0.00f, 1.20f, 1.08f, 1.20f, 0.002f, 0.30f, 0.50f, new float[]{0.50f, 0.52f, 0.62f}, new float[]{0.60f, 0.52f, 0.56f}, false);
        add("toxico",      "radiactivo: verdes acidos",                    0.45f, 0.00f, 1.30f, 0.95f, 1.00f, 0.005f, 0.50f, 0.65f, new float[]{0.44f, 0.58f, 0.44f}, new float[]{0.55f, 0.60f, 0.40f}, false);
        add("sangre",      "rojo sangre, todo teñido",                     0.60f, 0.20f, 1.10f, 0.85f, 0.95f, 0.006f, 1.00f, 0.90f, new float[]{0.60f, 0.40f, 0.40f}, new float[]{0.62f, 0.45f, 0.45f}, false);
    }
}
