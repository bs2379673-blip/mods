package com.annis.npc.client;

import java.util.List;

/**
 * Conjuntos: accesorios + animacion pensados para encajar entre si
 * (las posiciones salen de calcular donde caen las manos en cada animacion).
 */
public final class Outfits {
    public record Outfit(String name, String activity, String accessories) {
    }

    private static final String DRUM = "body|annisnpc:accesorio_tambor|0|0.00|13.50|-6.50|0.00|0.00|0.00|0.80";
    private static final String STICK_R = "right_arm|annisnpc:accesorio_baqueta|0|-1.00|9.50|1.50|95.00|0.00|0.00|0.60";
    private static final String STICK_L = "left_arm|annisnpc:accesorio_baqueta|0|1.00|9.50|1.50|95.00|0.00|0.00|0.60";
    private static final String TRUMPET = "head|annisnpc:accesorio_trompeta|0|0.00|-2.90|-9.00|0.00|180.00|0.00|0.60";
    private static final String HAT = "head|annisnpc:accesorio_sombrero|0|0.00|-13.40|0.00|0.00|0.00|0.00|0.62";
    private static final String BATON = "right_arm|annisnpc:accesorio_baqueta|0|-1.00|9.50|-2.50|-90.00|0.00|0.00|0.50";

    public static final List<Outfit> ALL = List.of(
            new Outfit("Tamborilero", "tocar_tambor", String.join(";", DRUM, STICK_R, STICK_L)),
            new Outfit("Tambor de desfile", "desfile_tambor", String.join(";", DRUM, STICK_R, STICK_L)),
            new Outfit("Trompetista", "tocar_trompeta", TRUMPET),
            new Outfit("Presentador", "presentar", HAT),
            new Outfit("Caballero (saludo)", "saludo_sombrero", HAT),
            new Outfit("Director de banda", "dirigir", String.join(";", HAT, BATON)),
            new Outfit("Soldado en marcha", "marchar", HAT));

    private Outfits() {
    }
}
