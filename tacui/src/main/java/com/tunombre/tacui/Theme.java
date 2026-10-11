package com.tunombre.tacui;

/** Paleta de colores (ARGB). El rojo oscuro es el tema por defecto. */
public enum Theme {
    RED(0xFFD83A3A, 0xFF5A1A1A, 0xEE120808, 0xF01C1010, 0xFFEBD9D9),
    BLUE(0xFF5B7CFF, 0xFF1F2C66, 0xEE080A14, 0xF010142A, 0xFFD9E0F5),
    GREEN(0xFF39D98A, 0xFF14502F, 0xEE07100B, 0xF00C1A12, 0xFFD6F2E3),
    ORANGE(0xFFFF8A2A, 0xFF603210, 0xEE120B06, 0xF01E140C, 0xFFF5E4D2),
    BLACKOUT(0xFFD0D0D0, 0xFF3C3C3C, 0xEE050505, 0xF00F0F0F, 0xFFE6E6E6);

    public final int accent, dim, bg, panel, text;

    Theme(int accent, int dim, int bg, int panel, int text) {
        this.accent = accent; this.dim = dim; this.bg = bg; this.panel = panel; this.text = text;
    }

    public Theme next() { Theme[] v = values(); return v[(ordinal() + 1) % v.length]; }

    public static Theme of(String name) {
        try { return valueOf(name.toUpperCase()); } catch (Exception e) { return RED; }
    }

    /** Mezcla el color con alfa (0..255). */
    public static int alpha(int argb, int a) { return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24); }
}
