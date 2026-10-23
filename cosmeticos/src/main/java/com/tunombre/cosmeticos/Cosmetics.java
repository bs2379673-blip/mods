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
        // --- nuevos (se agregan al final: el orden es el bit que viaja por red)
        add("aura_67", "Aura 6-7", 0xFFC040);
        add("aura_sangre", "Aura de sangre", 0x8A0010);
        add("aura_glitch", "Aura glitch", 0x20E8FF);
        add("espiritus", "Espiritus errantes", 0xA0F0FF);
        add("petalos_mortifilia", "Lluvia de petalos de Mortifilia", 0xE080FF);
        add("cenizas", "Cenizas y brasas", 0x605050);
        add("cadenas", "Cadenas espectrales", 0x80A8FF);
        add("nube_tormenta", "Nube de tormenta", 0x506070);
        add("luz_divina", "Luz divina", 0xFFE6A0);
        add("aureola_rota", "Aureola rota", 0xFF2A2A);
        add("tercer_ojo", "Tercer ojo", 0xB040FF);
        add("corona_mortifilia", "Corona de Mortifilia", 0xD060FF);
        add("tentaculos_abismo", "Tentaculos del abismo", 0x8A40FF);
        add("alas_cuervo", "Alas de cuervo", 0x1A1A1E);
        // solo administradores (modo dios oscuro)
        add("alas_dios", "Alas del dios oscuro (admin)", 0x200008);
        add("guadana_dios", "Guadana del dios oscuro (admin)", 0xFF1020);
    }

    public static Def byId(String id) {
        for (Def d : ALL) if (d.id().equals(id)) return d;
        return null;
    }
}
