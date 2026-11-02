package com.starlevi.starriders.server;

import com.starlevi.starriders.Config;
import com.starlevi.starriders.ModRegistry;
import com.starlevi.starriders.PhoenixEntity;
import com.starlevi.starriders.RiderData;
import com.starlevi.starriders.WhistleItem;
import com.starlevi.starriders.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Llamar y guardar monturas, domar salvajes y la aparicion muy rara de una salvaje. */
public final class Mounts {
    private static final Map<UUID, PhoenixEntity> LIVE = new HashMap<>();
    private static PhoenixEntity wild;

    private Mounts() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                PacketDistributor.sendToPlayer(p, new Net.Settings(Config.flySpeed, Config.flyBoost));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                for (RiderData.Mount m : data(p).mounts) {
                    PhoenixEntity ph = LIVE.remove(m.id());
                    if (ph != null) {
                        ph.ejectPassengers();
                        ph.discard();
                    }
                }
            }
        });
    }

    public static RiderData data(ServerPlayer p) {
        return p.getData(ModRegistry.DATA.get());
    }

    public static String displayName(String type) {
        return "fenix".equals(type) ? "Fénix" : type;
    }

    /** Admin: le da una montura nueva (y su silbato) a un jugador. */
    public static RiderData.Mount give(ServerPlayer p, String type) {
        RiderData.Mount m = new RiderData.Mount(UUID.randomUUID(), type);
        data(p).mounts.add(m);
        p.getInventory().placeItemBackInInventory(WhistleItem.make(p, m));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
        p.sendSystemMessage(Component.literal("🔥 Recibiste una montura: ").append(Component.literal(displayName(type)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(". Usá el silbato para llamarla.")));
        return m;
    }

    /** Toque del silbato: si esta cerca la guarda; si no, la llama. */
    public static void whistle(ServerPlayer p, UUID mountId) {
        RiderData.Mount m = data(p).find(mountId);
        if (m == null) {
            p.displayClientMessage(Component.literal("Esta montura ya no es tuya"), true);
            return;
        }
        PhoenixEntity ph = LIVE.get(mountId);
        if (ph != null && ph.isAlive() && ph.level() == p.level()) {
            if (ph.distanceTo(p) < 40 && !ph.hasPassenger(p)) {
                recall(ph, false);
                p.displayClientMessage(Component.literal("🔥 " + displayName(m.type()) + " vuelve al silbato"), true);
            } else if (!ph.hasPassenger(p)) {
                Vec3 at = spotNear(p);
                ph.teleportTo(at.x, at.y, at.z);
                ph.roar();
                burst((ServerLevel) p.level(), at);
            }
            return;
        }
        if (ph != null) {
            ph.discard();
        }
        summon(p, m);
    }

    private static Vec3 spotNear(ServerPlayer p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 0.01) {
            look = new Vec3(0, 0, 1);
        }
        Vec3 at = p.position().add(look.normalize().scale(3.5));
        Level level = p.level();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        double y = top <= p.getY() + 4 ? top : p.getY();
        return new Vec3(at.x, y, at.z);
    }

    private static void summon(ServerPlayer p, RiderData.Mount m) {
        ServerLevel level = p.serverLevel();
        PhoenixEntity ph = ModRegistry.PHOENIX.get().create(level);
        if (ph == null) {
            return;
        }
        Vec3 at = spotNear(p);
        ph.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
        ph.setOwner(p.getUUID());
        ph.mountId = m.id();
        level.addFreshEntity(ph);
        LIVE.put(m.id(), ph);
        ph.roar();
        burst(level, at);
    }

    /** Vuelve al silbato (con llamas). */
    public static void recall(PhoenixEntity ph, boolean idle) {
        if (ph.level() instanceof ServerLevel sl) {
            burst(sl, ph.position());
            sl.playSound(null, ph.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8f, 1.3f);
            if (idle && ph.ownerId() != null && sl.getServer().getPlayerList().getPlayer(ph.ownerId()) instanceof ServerPlayer o) {
                o.displayClientMessage(Component.literal("🔥 Tu fénix volvió al silbato a descansar"), true);
            }
        }
        if (ph.mountId != null) {
            LIVE.remove(ph.mountId);
        }
        ph.ejectPassengers();
        ph.discard();
    }

    private static void burst(ServerLevel l, Vec3 at) {
        l.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.2, at.z, 60, 1.0, 1.0, 1.0, 0.06);
        l.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 12, 0.8, 0.5, 0.8, 0);
        l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 25, 0.8, 1.0, 0.8, 0.08);
    }

    /** Se domo una salvaje: pasa a ser del jugador, con su silbato. */
    public static void tame(PhoenixEntity ph, ServerPlayer p) {
        RiderData.Mount m = new RiderData.Mount(UUID.randomUUID(), "fenix");
        data(p).mounts.add(m);
        ph.setWild(false);
        ph.setOwner(p.getUUID());
        ph.mountId = m.id();
        ph.setFlying(false);
        LIVE.put(m.id(), ph);
        if (wild == ph) {
            wild = null;
        }
        p.getInventory().placeItemBackInInventory(WhistleItem.make(p, m));
        ph.roar();
        burst(p.serverLevel(), ph.position());
        p.level().playSound(null, ph.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Component all = Component.literal("🔥 " + p.getGameProfile().getName() + " domó un ").append(Component.literal("Fénix salvaje")
                .withStyle(ChatFormatting.GOLD)).append(Component.literal("!"));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            o.sendSystemMessage(all);
        }
    }

    /** Admin o suerte: aparece una salvaje en ese lugar. */
    public static PhoenixEntity spawnWild(ServerLevel level, Vec3 at) {
        if (wild != null && wild.isAlive()) {
            wild.discard();
        }
        PhoenixEntity ph = ModRegistry.PHOENIX.get().create(level);
        if (ph == null) {
            return null;
        }
        ph.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(ph);
        ph.setWild(true);
        wild = ph;
        // un canto que se escucha de lejos
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 6f, 1.8f);
        burst(level, at);
        return ph;
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, PhoenixEntity>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            if (!it.next().getValue().isAlive()) {
                it.remove();
            }
        }
        if (wild != null && !wild.isAlive()) {
            wild = null;
        }
        if (server.getTickCount() % 1200 != 0 || wild != null || Config.wildChancePerMinute <= 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.level().dimension() != Level.OVERWORLD || p.getRandom().nextDouble() >= Config.wildChancePerMinute) {
                continue;
            }
            ServerLevel level = p.serverLevel();
            double a = p.getRandom().nextDouble() * Math.PI * 2, r = 45 + p.getRandom().nextDouble() * 40;
            int x = Mth.floor(p.getX() + Math.cos(a) * r), z = Mth.floor(p.getZ() + Math.sin(a) * r);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
                continue;
            }
            spawnWild(level, new Vec3(x + 0.5, y, z + 0.5));
            p.displayClientMessage(Component.literal("🔥 Se escucha un canto de fuego a lo lejos…").withStyle(ChatFormatting.GOLD), true);
            return;
        }
    }

    public static Map<UUID, PhoenixEntity> live() {
        return LIVE;
    }
}
