package com.starlevi.starspirits.server;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La caravana: cada espiritu de un jugador existe como entidad y corre atras del anterior (el primero,
 * atras del jugador). Si queda lejos o el jugador cambia de dimension, aparece a su lado. Al salir del
 * servidor desaparecen y al volver aparecen de nuevo.
 */
public final class Caravan {
    /** Entidad viva de cada espiritu (id de la entrada -> entidad). */
    private static final Map<UUID, SpiritEntity> LIVE = new HashMap<>();

    private Caravan() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                SpiritLibrary.send(p);
                Pending.giveBack(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                dismiss(p);
                Uploads.left(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                dismiss(p);
            }
        });
    }

    public static String displayName(String type) {
        SpiritDef d = SpiritLibrary.get(type);
        return d == null ? type : d.name;
    }

    public static SpiritData data(ServerPlayer p) {
        return p.getData(ModRegistry.DATA.get());
    }

    /** Saca de la vista los espiritus del jugador (siguen en sus datos). */
    public static void dismiss(ServerPlayer p) {
        for (SpiritData.Entry e : data(p).entries) {
            SpiritEntity s = LIVE.remove(e.id());
            if (s != null) {
                s.discard();
            }
        }
    }

    public static void setActive(ServerPlayer p, UUID id) {
        SpiritData d = data(p);
        SpiritData.Entry e = d.find(id);
        if (e == null) {
            return;
        }
        d.active = id;
        SpiritDef def = SpiritLibrary.get(e.type());
        p.displayClientMessage(Component.literal("✦ ").append(Component.literal(displayName(e.type()))
                .withStyle(def == null ? net.minecraft.ChatFormatting.WHITE : def.rarity.chat))
                .append(Component.literal(" es tu espíritu activo")), true);
        Benefits.changed(p);
    }

    /** Un espiritu nuevo que ya existe como entidad (recien extraido): entra a la caravana. */
    public static void adopt(ServerPlayer p, SpiritData.Entry e, SpiritEntity s) {
        s.entryId = e.id();
        s.setOwner(p.getUUID());
        s.setSpiritType(e.type());
        LIVE.put(e.id(), s);
    }

    public static SpiritEntity live(UUID entry) {
        SpiritEntity s = LIVE.get(entry);
        return s != null && s.isAlive() ? s : null;
    }

    /** Saca un espiritu de la caravana con una nubecita (se fue a una maceta, lo quitaron...). */
    public static void poof(UUID entry) {
        SpiritEntity s = LIVE.remove(entry);
        if (s != null) {
            if (s.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, s.getX(), s.getY() + 0.4, s.getZ(), 12, 0.25, 0.25, 0.25, 0.02);
            }
            s.discard();
        }
    }

    private static void tick(MinecraftServer server) {
        SpiritLibrary.tick(server);
        Uploads.tick(server);
        int now = server.getTickCount();
        // espiritus huerfanos (su entrada ya no existe o su dueno se fue)
        for (Iterator<Map.Entry<UUID, SpiritEntity>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, SpiritEntity> en = it.next();
            SpiritEntity s = en.getValue();
            ServerPlayer owner = s.ownerId() == null ? null : server.getPlayerList().getPlayer(s.ownerId());
            if (!s.isAlive() || owner == null || data(owner).find(en.getKey()) == null) {
                s.discard();
                it.remove();
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SpiritData d = data(p);
            if (d.entries.isEmpty()) {
                continue;
            }
            SpiritData.Entry active = d.activeEntry();
            ServerLevel level = p.serverLevel();
            Entity leader = p;
            int n = 0;
            // jugadores de antes (sin elegidos): los primeros que tenga
            if (d.followers.isEmpty() && !d.followersChosen) {
                d.followersChosen = true;
                for (SpiritData.Entry e : d.entries) {
                    if (d.followers.size() < Config.maxFollowers && !Homes.isHome(e.id())) {
                        d.followers.add(e.id());
                    }
                }
            }
            for (SpiritData.Entry e : new ArrayList<>(d.entries)) {
                SpiritEntity s = LIVE.get(e.id());
                if (n >= Config.maxFollowers || p.isSpectator() || !d.follows(e.id())) {
                    if (s != null) {
                        LIVE.remove(e.id());
                        s.discard();
                    }
                    continue;
                }
                if (s == null || !s.isAlive() || s.level() != level) {
                    if (s != null) {
                        s.discard();
                    }
                    s = spawnNear(level, p, leader, e);
                    if (s == null) {
                        continue;
                    }
                }
                s.setActiveSpirit(active != null && active.id().equals(e.id()));
                follow(s, leader, p, now, n);
                leader = s;
                n++;
            }
        }
        Benefits.tick(server);
    }

    private static SpiritEntity spawnNear(ServerLevel level, ServerPlayer p, Entity leader, SpiritData.Entry e) {
        SpiritEntity s = ModRegistry.SPIRIT.get().create(level);
        if (s == null) {
            return null;
        }
        Vec3 at = behind(leader, 1.0);
        s.moveTo(at.x, leader.getY(), at.z, leader.getYRot(), 0);
        adopt(p, e, s);
        level.addFreshEntity(s);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, leader.getY() + 0.4, at.z, 6, 0.2, 0.2, 0.2, 0.01);
        return s;
    }

    private static Vec3 behind(Entity e, double dist) {
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        return e.position().add(Math.sin(r) * dist, 0, -Math.cos(r) * dist);
    }

    /** Correr atras del que va adelante; si quedo muy lejos, aparecer cerca. */
    private static void follow(SpiritEntity s, Entity leader, ServerPlayer owner, int now, int index) {
        double dist = s.distanceTo(leader);
        if (dist > 20 || (owner.isFallFlying() && dist > 10)) {
            Vec3 at = behind(leader, 1.0);
            BlockPos ground = BlockPos.containing(at.x, leader.getY(), at.z);
            s.teleportTo(at.x, ground.getY(), at.z);
            s.getNavigation().stop();
            if (s.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, ground.getY() + 0.4, at.z, 5, 0.2, 0.2, 0.2, 0.01);
            }
            return;
        }
        s.getLookControl().setLookAt(owner, 30, 30);
        if ((now + index) % 4 != 0) {
            return;
        }
        double gap = index == 0 ? 1.6 : 1.1;
        if (dist > gap) {
            // en zigzag (uno a cada lado) para que se vean todos atras tuyo
            Vec3 at = behind(leader, gap * 0.8);
            if (index > 0) {
                float yaw = leader instanceof LivingEntity le ? le.yBodyRot : leader.getYRot();
                double r = Math.toRadians(yaw), side = (index % 2 == 0 ? 0.55 : -0.55);
                at = at.add(Math.cos(r) * side, 0, Math.sin(r) * side);
            }
            double speed = dist > 6 ? 1.75 : dist > 3 ? 1.4 : 1.1;
            s.getNavigation().moveTo(at.x, leader.getY(), at.z, speed);
        } else {
            s.getNavigation().stop();
        }
    }

    /** Agrega un espiritu a un jugador (comando, maceta, extraccion sin entidad). */
    public static SpiritData.Entry give(ServerPlayer p, String type) {
        SpiritData.Entry e = data(p).add(type);
        Benefits.changed(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        return e;
    }

    public static List<UUID> ids(ServerPlayer p) {
        List<UUID> out = new ArrayList<>();
        for (SpiritData.Entry e : data(p).entries) {
            out.add(e.id());
        }
        return out;
    }
}
