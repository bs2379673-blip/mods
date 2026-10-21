package com.tunombre.cosmeticos.geo;

import com.tunombre.cosmeticos.geo.cos.*;

import java.util.HashMap;
import java.util.Map;

/** todos los cosmeticos por id */
public final class Reg {
    private Reg() { }

    private static final Map<String, Cosm> ALL = new HashMap<>();

    public static Cosm get(String id) { return ALL.get(id); }

    private static void add(String id, Cosm c) { ALL.put(id, c); }

    static {
        add("alas_luz", new FanWings("alas_luz", FanWings.Kind.FEATHER, "feather_luz", "feather_luz_g", 0.95F, 0.2F, 12, 0xFFFFFFFF, 0xFFFFD27A));
        add("alas_fuego", new FanWings("alas_fuego", FanWings.Kind.FLAME, "flame_wing", "flame_wing_g", 1.0F, 0.22F, 14, 0xFFFFFFFF, 0xFFFF7A18));
        add("alas_cristal", new FanWings("alas_cristal", FanWings.Kind.CRYSTAL, "crystal", "crystal_g", 1.0F, 0.2F, 11, 0xE8FFFFFF, 0xFF7FE8FF));
        add("alas_mortifilia", new BatWings("alas_mortifilia", "membrane_mort", "membrane_mort_g", "bone_mort", 1.0F, 4, 0xFFFFFFFF, 0xFFD058FF, true));
        add("alas_demonio", new BatWings("alas_demonio", "membrane_demon", "membrane_demon_g", "bone_demon", 1.25F, 4, 0xFFFFFFFF, 0xFFFF4A18, false));
        add("alas_mariposa", new Butterfly());
        add("tentaculos_slender", new Tentacles("tentaculos_slender", Cosm.Bone.CHEST, "tentacle_slender", null, new Tentacles.Spec[]{
                new Tentacles.Spec(V3.of(0.12, -0.30, -0.14), V3.of(0.45, 0.70, -0.55), 1.9F, -0.075F, 0.01F, 0F),
                new Tentacles.Spec(V3.of(0.20, -0.50, -0.14), V3.of(0.80, 0.35, -0.50), 1.7F, -0.09F, 0.02F, 1.3F),
                new Tentacles.Spec(V3.of(0.10, -0.70, -0.14), V3.of(0.50, -0.05, -0.85), 1.5F, -0.05F, -0.02F, 2.6F)},
                true, 18, 0.034F, 0.05F, 0xFFFFFFFF, 0, 0, 0.012F));
        add("tentaculos_mortifilia", new Tentacles("tentaculos_mortifilia", Cosm.Bone.CHEST, "tentacle_mort", "tentacle_mort_g", new Tentacles.Spec[]{
                new Tentacles.Spec(V3.of(0.13, -0.28, -0.14), V3.of(0.50, 0.65, -0.55), 1.35F, -0.09F, 0.02F, 0.5F),
                new Tentacles.Spec(V3.of(0.20, -0.50, -0.14), V3.of(0.85, 0.30, -0.45), 1.2F, -0.11F, -0.02F, 1.9F),
                new Tentacles.Spec(V3.of(0.10, -0.68, -0.14), V3.of(0.45, -0.05, -0.88), 1.05F, -0.07F, 0.03F, 3.1F)},
                true, 16, 0.045F, 0.06F, 0xFFFFFFFF, 0xFFE070FF, 1, 0.014F));
        add("cola_demonio", new Tentacles("cola_demonio", Cosm.Bone.PELVIS, "tail_demon", "tail_demon_g", new Tentacles.Spec[]{
                new Tentacles.Spec(V3.of(0, -0.02, -0.13), V3.of(0, -0.30, -0.95), 1.15F, 0.02F, 0.0F, 0F)},
                false, 16, 0.045F, 0.07F, 0xFFFFFFFF, 0xFFFF5A20, 2, 0.02F));
        add("flores_mortifilia", Flora.bodyFlowers());
        add("enredadera", Flora.vines());
        add("flor_cabeza", Flora.headFlower());
        add("aureola", HeadGear.halo());
        add("corona_espinas", HeadGear.thorns());
        add("cuernos_demonio", HeadGear.horns());
        add("aura_fuego", Fx.fire());
        add("aura_oscura", Fx.dark());
        add("orbitas", Fx.orbs());
        add("anillos_arcanos", Fx.rings());
        add("ojos_flotantes", Fx.eyes());
        add("estrellas", Fx.stars());
        add("rayos", Fx.lightning());
        add("mariposas_luz", Fx.butterflies());
        add("luciernagas", Fx.fireflies());
    }
}
