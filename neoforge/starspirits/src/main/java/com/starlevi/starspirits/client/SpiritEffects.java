package com.starlevi.starspirits.client;

import com.starlevi.starspirits.Rarity;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.SpiritEntity;
import com.starlevi.starspirits.block.ExtractionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/** Particulas del lado del cliente: polvito al correr, brillitos de los raros y la espiral de petalos al extraer. */
public final class SpiritEffects {
    private static final Map<String, ParticleOptions> PARTICLES = new HashMap<>();

    private SpiritEffects() {
    }

    private static ParticleOptions particle(String id) {
        if (id == null) {
            return null;
        }
        return PARTICLES.computeIfAbsent(id, k -> {
            try {
                return BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(k)) instanceof SimpleParticleType s ? s : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    public static void tick(SpiritEntity e) {
        Level level = e.level();
        RandomSource r = level.random;
        SpiritDef d = ClientDefs.def(e.spiritType());
        int age = e.tickCount + e.getId();
        double speed = e.getDeltaMovement().horizontalDistance();
        if (speed > 0.12 && e.onGround() && age % 5 == 0) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, e.getX(), e.getY() + 0.05, e.getZ(), 0, 0.01, 0);
        }
        ParticleOptions own = d == null ? null : particle(d.particle);
        if (own != null && age % 14 == 0) {
            level.addParticle(own, e.getX() + (r.nextDouble() - 0.5) * 0.4, e.getY() + 0.5 + r.nextDouble() * 0.3,
                    e.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
        Rarity rarity = d == null ? Rarity.COMUN : d.rarity;
        if (rarity == Rarity.LEGENDARIO && age % 9 == 0) {
            level.addParticle(ParticleTypes.END_ROD, e.getX() + (r.nextDouble() - 0.5) * 0.6, e.getY() + 0.3 + r.nextDouble() * 0.6,
                    e.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.005, 0);
        } else if (rarity == Rarity.EPICO && age % 16 == 0) {
            level.addParticle(ParticleTypes.WAX_ON, e.getX() + (r.nextDouble() - 0.5) * 0.5, e.getY() + 0.4 + r.nextDouble() * 0.4,
                    e.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }

    public static void extraction(ExtractionBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        RandomSource r = level.random;
        BlockPos p = be.getBlockPos();
        double cx = p.getX() + 0.5, cz = p.getZ() + 0.5;
        long now = level.getGameTime();
        if (be.phase == ExtractionBlockEntity.READY) {
            if (now % 12 == 0) {
                level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, cx + (r.nextDouble() - 0.5) * 0.8, p.getY() + 1.3 + r.nextDouble() * 0.4,
                        cz + (r.nextDouble() - 0.5) * 0.8, 0, 0, 0);
            }
            if (now % 30 == 0) {
                level.addParticle(ParticleTypes.CHERRY_LEAVES, cx + (r.nextDouble() - 0.5), p.getY() + 1.6, cz + (r.nextDouble() - 0.5), 0, 0, 0);
            }
            return;
        }
        if (be.phase != ExtractionBlockEntity.EXTRACTING) {
            return;
        }
        float f = Mth.clamp((now - be.start) / (float) Math.max(20, be.duration), 0, 1);
        // espiral de petalos que sube y se cierra hacia la semilla
        int n = 1 + (int) (f * 3);
        for (int i = 0; i < n; i++) {
            double a = now * 0.35 + i * Math.PI * 2 / n + r.nextDouble() * 0.3;
            double rad = 1.3 - f * 0.8;
            double y = p.getY() + 0.2 + r.nextDouble() * 1.2;
            level.addParticle(ParticleTypes.CHERRY_LEAVES, cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad,
                    -Math.sin(a) * 0.08, 0.04, Math.cos(a) * 0.08);
        }
        if (f > 0.3f && now % 2 == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx + (r.nextDouble() - 0.5) * 0.3, p.getY() + 1.4, cz + (r.nextDouble() - 0.5) * 0.3,
                    0, 0.25 + r.nextDouble() * 0.2, 0);
        }
        if (f < 0.75f && now % 3 == 0) {
            // el piso tiembla: polvito
            level.addParticle(ParticleTypes.WHITE_SMOKE, cx + (r.nextDouble() - 0.5) * 1.6, p.getY() + 0.05, cz + (r.nextDouble() - 0.5) * 1.6,
                    0, 0.02, 0);
        }
    }
}
