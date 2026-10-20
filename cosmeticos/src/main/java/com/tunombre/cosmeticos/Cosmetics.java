package com.tunombre.cosmeticos;

import java.util.ArrayList;
import java.util.List;

/** catalogo de cosmeticos: el indice es el bit que viaja por red (maximo 64) */
public final class Cosmetics {
    private Cosmetics() { }

    public record Def(int index, String id, String nombre, int color) { }

    public static final List<Def> ALL = new ArrayList<>();

    private static void add(String id, String nombre, int color) { ALL.add(new Def(ALL.size(), id, nombre, color)); }

    static {
        // alas
        add("alas_luz", "Alas de luz", 0xFFE9A0);
        add("alas_mortifilia", "Alas de Mortifilia", 0xA040E0);
        add("alas_demonio", "Alas de demonio", 0xB01818);
        add("alas_cristal", "Alas de cristal", 0x60E8FF);
        add("alas_fuego", "Alas de fuego", 0xFF8A10);
        add("alas_mariposa", "Alas de mariposa", 0xFF70D0);
        // tentaculos y cola
        add("tentaculos_slender", "Tentaculos de sombra (Slender)", 0x151515);
        add("tentaculos_mortifilia", "Tentaculos de Mortifilia", 0x8030C0);
        add("cola_demonio", "Cola de demonio", 0x901010);
        // flores
        add("flores_mortifilia", "Flores de Mortifilia en el cuerpo", 0xC050FF);
        add("enredadera", "Enredadera", 0x40B040);
        add("flor_cabeza", "Flor de Mortifilia en la cabeza", 0xD070FF);
        // cabeza
        add("aureola", "Aureola", 0xFFD860);
        add("corona_espinas", "Corona de espinas", 0x802020);
        add("cuernos_demonio", "Cuernos de demonio", 0x701010);
        // auras
        add("aura_fuego", "Aura de fuego", 0xFF6000);
        add("aura_oscura", "Aura oscura", 0x302040);
        add("orbitas", "Orbes orbitando", 0x60C0FF);
        add("anillos_arcanos", "Anillos arcanos", 0x9060FF);
        add("ojos_flotantes", "Ojos flotantes", 0xFF2020);
        add("estrellas", "Estrellas", 0xFFF0A0);
        add("rayos", "Rayos", 0x80D8FF);
        add("mariposas_luz", "Mariposas de luz", 0x80FFE0);
        add("luciernagas", "Luciernagas", 0xC8FF50);
    }

    public static Def byId(String id) {
        for (Def d : ALL) if (d.id().equals(id)) return d;
        return null;
    }
}
