package com.starlevi.starriders.server;

import com.starlevi.starriders.Config;
import com.starlevi.starriders.GuardianEntity;
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
    /** Tipos de montura que existen. */
    public static final java.util.List<String> TYPES = java.util.List.of("fenix", "guardian");

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
        return switch (type) {
            case "fenix" -> "Fénix";
            case "guardian" -> "Guardián del Bosque";
            default -> type;
        };
    }

    private static String icon(String type) {
        return "guardian".equals(type) ? "🌳" : "🔥";
    }

    private static PhoenixEntity create(ServerLevel level, String type) {
        return "guardian".equals(type) ? ModRegistry.GUARDIAN.get().create(level) : ModRegistry.PHOENIX.get().create(level);
    }

    /** Admin: le da una montura nueva (y su silbato) a un jugador. */
    public static RiderData.Mount give(ServerPlayer p, String type) {
        RiderData.Mount m = new RiderData.Mount(UUID.randomUUID(), type);
        data(p).mounts.add(m);
        p.getInventory().placeItemBackInInventory(WhistleItem.make(p, m));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
        p.sendSystemMessage(Component.literal(icon(type) + " Recibiste una montura: ").append(Component.literal(displayName(type)).withStyle(ChatFormatting.GOLD))
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
                p.displayClientMessage(Component.literal(icon(m.type()) + " " + displayName(m.type()) + " vuelve al silbato"), true);
            } else if (!ph.hasPassenger(p)) {
                Vec3 at = spotNear(p);
                ph.teleportTo(at.x, at.y, at.z);
                ph.roar();
                burst((ServerLevel) p.level(), at, m.type());
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
        PhoenixEntity ph = create(level, m.type());
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
        burst(level, at, m.type());
    }

    /** Vuelve al silbato (con llamas). */
    public static void recall(PhoenixEntity ph, boolean idle) {
        if (ph.level() instanceof ServerLevel sl) {
            burst(sl, ph.position(), ph.mountType());
            sl.playSound(null, ph.blockPosition(), ph instanceof GuardianEntity ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.FIRECHARGE_USE,
                    SoundSource.NEUTRAL, 0.8f, ph instanceof GuardianEntity ? 0.7f : 1.3f);
            if (idle && ph.ownerId() != null && sl.getServer().getPlayerList().getPlayer(ph.ownerId()) instanceof ServerPlayer o) {
                o.displayClientMessage(Component.literal(ph.icon() + " Tu " + ph.label() + " volvió al silbato a descansar"), true);
            }
        }
        if (ph.mountId != null) {
            LIVE.remove(ph.mountId);
        }
        ph.ejectPassengers();
        ph.discard();
    }

    private static void burst(ServerLevel l, Vec3 at, String type) {
        if ("guardian".equals(type)) {
            l.sendParticles(ParticleTypes.COMPOSTER, at.x, at.y + 1.2, at.z, 50, 1.0, 1.0, 1.0, 0.05);
            l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 1.0, at.z, 40, 1.2, 0.8, 1.2, 0);
            l.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y + 2.0, at.z, 20, 1.0, 0.6, 1.0, 0);
            return;
        }
        l.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.2, at.z, 60, 1.0, 1.0, 1.0, 0.06);
        l.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 12, 0.8, 0.5, 0.8, 0);
        l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 25, 0.8, 1.0, 0.8, 0.08);
    }

    /** Se domo una salvaje: pasa a ser del jugador, con su silbato. */
    public static void tame(PhoenixEntity ph, ServerPlayer p) {
        RiderData.Mount m = new RiderData.Mount(UUID.randomUUID(), ph.mountType());
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
        burst(p.serverLevel(), ph.position(), ph.mountType());
        p.level().playSound(null, ph.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Component all = Component.literal(ph.icon() + " " + p.getGameProfile().getName() + " domó un ").append(Component.literal(displayName(ph.mountType()) + " salvaje")
                .withStyle(ChatFormatting.GOLD)).append(Component.literal("!"));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            o.sendSystemMessage(all);
        }
    }

    /** Admin o suerte: aparece una salvaje en ese lugar. */
    public static PhoenixEntity spawnWild(ServerLevel level, Vec3 at, String type) {
        if (wild != null && wild.isAlive()) {
            wild.discard();
        }
        if (type == null) {
            type = TYPES.get(level.random.nextInt(TYPES.size()));
        }
        PhoenixEntity ph = create(level, type);
        if (ph == null) {
            return null;
        }
        ph.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(ph);
        ph.setWild(true);
        wild = ph;
        // un canto (o un rugido) que se escucha de lejos
        if (ph instanceof GuardianEntity) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 6f, 0.6f);
        } else {
            level.playSound(null, BlockPos.containing(at), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 6f, 1.8f);
        }
        burst(level, at, type);
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
            PhoenixEntity w = spawnWild(level, new Vec3(x + 0.5, y, z + 0.5), null);
            p.displayClientMessage(Component.literal(w instanceof GuardianEntity ? "🌳 La tierra retumba a lo lejos… algo enorme se despertó"
                    : "🔥 Se escucha un canto de fuego a lo lejos…").withStyle(ChatFormatting.GOLD), true);
            return;
        }
    }

    public static Map<UUID, PhoenixEntity> live() {
        return LIVE;
    }
}
