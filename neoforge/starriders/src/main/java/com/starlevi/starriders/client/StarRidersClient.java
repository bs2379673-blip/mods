package com.starlevi.starriders.client;

import com.starlevi.starriders.ModRegistry;
import com.starlevi.starriders.GuardianEntity;
import com.starlevi.starriders.PhoenixEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Lado del cliente: dibujo, estela de fuego, camara mas lejos al montar y Do a Barrel Roll. */
public final class StarRidersClient {
    private StarRidersClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ModRegistry.PHOENIX.get(), PhoenixRenderer::new);
            e.registerEntityRenderer(ModRegistry.GUARDIAN.get(), GuardianRenderer::new);
            e.registerEntityRenderer(ModRegistry.SERPENT.get(), SerpentRenderer::new);
        });
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(DabrCompat::init));
        PhoenixEntity.clientTick = StarRidersClient::particles;
        RiderPose.init();
        NeoForge.EVENT_BUS.addListener((CalculateDetachedCameraDistanceEvent e) -> {
            var p = Minecraft.getInstance().player;
            if (p != null && p.getVehicle() instanceof PhoenixEntity) {
                e.setDistance(e.getDistance() * (p.getVehicle() instanceof GuardianEntity ? 3.4f : 2.6f));
            }
        });
    }

    /** Estela de chispas y fuego al volar; brasas suaves posado. */
    private static void particles(PhoenixEntity e) {
        Level level = e.level();
        RandomSource r = level.random;
        if (e instanceof com.starlevi.starriders.SerpentEntity) {
            if (e.isWild() && e.tickCount % 6 == 0) {
                level.addParticle(ParticleTypes.WITCH, e.getX() + (r.nextDouble() - 0.5) * 2, e.getY() + 1 + r.nextDouble(),
                        e.getZ() + (r.nextDouble() - 0.5) * 2, 0, 0.02, 0);
            }
            return;
        }
        if (e instanceof GuardianEntity) {
            guardianParticles(e, level, r);
            return;
        }
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        float s = PhoenixRenderer.SCALE;
        if (e.isFlyingNow()) {
            Vec3 tail = e.position().add(fwd.scale(-2.6 * s)).add(0, 0.9 * s, 0);
            if (e.tickCount % 2 == 0) {
                level.addParticle(r.nextFloat() < 0.12f ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                        tail.x + (r.nextDouble() - 0.5) * 0.8, tail.y + (r.nextDouble() - 0.5) * 0.5, tail.z + (r.nextDouble() - 0.5) * 0.8,
                        0, 0.01, 0);
            }
            if (e.tickCount % 3 == 0) {
                for (int sd = -1; sd <= 1; sd += 2) {
                    Vec3 tip = e.position().add(side.scale(sd * 3.6 * s)).add(fwd.scale(-0.6 * s)).add(0, 1.0 * s, 0);
                    level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, -0.01, 0);
                }
            }
        } else if (e.tickCount % 9 == 0) {
            Vec3 at = e.position().add(fwd.scale(-1.4 * s)).add(0, 0.8 * s, 0);
            level.addParticle(ParticleTypes.SMALL_FLAME, at.x + (r.nextDouble() - 0.5), at.y, at.z + (r.nextDouble() - 0.5), 0, 0.015, 0);
        }
        if (e.isWild() && e.tickCount % 6 == 0) {
            level.addParticle(ParticleTypes.WAX_ON, e.getX() + (r.nextDouble() - 0.5) * 2, e.getY() + 1.5 + r.nextDouble(),
                    e.getZ() + (r.nextDouble() - 0.5) * 2, 0, 0.02, 0);
        }
    }

    /** Esporas y hojitas que se le caen de la melena; polvo al correr. */
    private static void guardianParticles(PhoenixEntity e, Level level, RandomSource r) {
        if (e.tickCount % 7 == 0) {
            level.addParticle(r.nextBoolean() ? ParticleTypes.SPORE_BLOSSOM_AIR : ParticleTypes.FALLING_SPORE_BLOSSOM,
                    e.getX() + (r.nextDouble() - 0.5) * 1.6, e.getY() + 1.6 + r.nextDouble() * 0.8, e.getZ() + (r.nextDouble() - 0.5) * 1.6, 0, 0, 0);
        }
        double hs = e.getDeltaMovement().horizontalDistance();
        if (hs > 0.2 && e.onGround() && e.tickCount % 2 == 0) {
            var below = level.getBlockState(e.blockPosition().below());
            if (!below.isAir()) {
                level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
                        e.getX() + (r.nextDouble() - 0.5) * 1.4, e.getY() + 0.1, e.getZ() + (r.nextDouble() - 0.5) * 1.4, 0, 0.05, 0);
            }
        }
        if (e.isWild() && e.tickCount % 6 == 0) {
            level.addParticle(ParticleTypes.COMPOSTER, e.getX() + (r.nextDouble() - 0.5) * 2, e.getY() + 1.5 + r.nextDouble(),
                    e.getZ() + (r.nextDouble() - 0.5) * 2, 0, 0.02, 0);
        }
    }
}
