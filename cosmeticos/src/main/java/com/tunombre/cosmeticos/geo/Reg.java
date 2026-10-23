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
        add("tentaculos_slender", new Tendrils("tentaculos_slender", Cosm.Bone.CHEST, "tentacle_slender", null, new Tendrils.T[]{
                new Tendrils.T(V3.of(0.10, -0.18, -0.15), V3.of(0.55, 0.60, -0.75), 2.0F, 0.16F, 0F),
                new Tendrils.T(V3.of(0.18, -0.34, -0.15), V3.of(1.00, 0.40, -0.50), 1.8F, 0.10F, 1.3F),
                new Tendrils.T(V3.of(0.08, -0.52, -0.15), V3.of(0.35, 0.80, -0.55), 1.55F, 0.20F, 2.6F)},
                true, 16, 0.062F, 0.012F, 0.32F, 0.004F, 0xFF0B0B0E, 0, Tendrils.Tip.NONE, true));
        add("tentaculos_mortifilia", new Tendrils("tentaculos_mortifilia", Cosm.Bone.CHEST, "tentacle_mort", "tentacle_mort_g", new Tendrils.T[]{
                new Tendrils.T(V3.of(0.12, -0.20, -0.15), V3.of(0.50, 0.65, -0.55), 1.45F, 0.12F, 0.5F),
                new Tendrils.T(V3.of(0.19, -0.40, -0.15), V3.of(0.85, 0.30, -0.45), 1.3F, 0.05F, 1.9F),
                new Tendrils.T(V3.of(0.09, -0.60, -0.15), V3.of(0.45, -0.05, -0.88), 1.15F, 0.0F, 3.1F)},
                true, 14, 0.055F, 0.016F, 0.28F, 0.005F, 0xFF6A3A88, 0xFFE070FF, Tendrils.Tip.FLOWER, true));
        add("cola_demonio", new Tendrils("cola_demonio", Cosm.Bone.PELVIS, "tail_demon", "tail_demon_g", new Tendrils.T[]{
                new Tendrils.T(V3.of(0, -0.02, -0.14), V3.of(0, -0.30, -0.95), 1.2F, 0.18F, 0F)},
                false, 14, 0.05F, 0.018F, 0.2F, 0.006F, 0xFF7A1010, 0xFFFF5A20, Tendrils.Tip.SPADE, false));
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
        add("aura_67", Extra.sixSeven());
        add("aura_sangre", Extra.blood());
        add("aura_glitch", Extra.glitch());
        add("espiritus", Extra.spirits());
        add("petalos_mortifilia", Extra.petals());
        add("cenizas", Extra.ashes());
        add("cadenas", Extra.chains());
        add("nube_tormenta", Extra.storm());
        add("luz_divina", Extra.divine());
        add("aureola_rota", Extra.brokenHalo());
        add("tercer_ojo", Extra.thirdEye());
        add("corona_mortifilia", Extra.mortCrown());
        add("tentaculos_abismo", new Tendrils("tentaculos_abismo", Cosm.Bone.CHEST, "tentacle_slender", "tentacle_mort_g", new Tendrils.T[]{
                new Tendrils.T(V3.of(0.08, -0.15, -0.15), V3.of(0.45, 0.75, -0.6), 2.4F, 0.2F, 0F),
                new Tendrils.T(V3.of(0.17, -0.30, -0.15), V3.of(1.00, 0.45, -0.45), 2.2F, 0.12F, 1.1F),
                new Tendrils.T(V3.of(0.20, -0.48, -0.15), V3.of(0.95, 0.05, -0.55), 2.0F, 0.06F, 2.2F),
                new Tendrils.T(V3.of(0.07, -0.62, -0.15), V3.of(0.30, -0.20, -0.95), 1.8F, 0.0F, 3.3F)},
                true, 16, 0.066F, 0.014F, 0.36F, 0.004F, 0xFF140A1E, 0xFF8A40FF, Tendrils.Tip.EYE, true));
        add("alas_cuervo", new FanWings("alas_cuervo", FanWings.Kind.FEATHER, "feather_dark", "feather_dark_g", 1.05F, 0.22F, 13, 0xFF2A2830, 0xFFFF2030));
        add("alas_dios", God.wings());
        add("guadana_dios", God.scythe());
    }
}
