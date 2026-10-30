package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.compat.ServerTickEvents;
import com.annis.npc.compat.EntityTrackingEvents;
import com.annis.npc.compat.PlayerLookup;
import com.annis.npc.compat.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Animaciones y aspecto de NPC Annis para CUALQUIER entidad viva (aldeanos de MCA, mobs...).
 * El aspecto se guarda en la entidad (persistente); la animacion es temporal y se sincroniza con
 * el mismo reloj comun que los NPC.
 */
public final class EntityLinks {
    /** El aspecto se guarda en los datos persistentes de la entidad. */
    private static final String LOOK = "annisnpc_look";

    private static CompoundTag getLook(net.minecraft.world.entity.Entity e) {
        CompoundTag data = e.getPersistentData();
        return data.contains(LOOK, 10) ? data.getCompound(LOOK) : null;
    }

    private record Anim(String name, int start, int serial, long until) {
    }

    private static final Map<UUID, Anim> ANIMS = new HashMap<>();
    private static int serialCounter = 1;

    private EntityLinks() {
    }

    public static void init() {
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (!(entity instanceof LivingEntity living) || entity instanceof AnnisNpcEntity) {
                return;
            }
            CompoundTag look = getLook(living);
            if (look != null && !look.isEmpty()) {
                ServerPlayNetworking.send(player, new EntityLookPayload(entity.getId(), look));
            }
            Anim a = ANIMS.get(entity.getUUID());
            if (a != null) {
                ServerPlayNetworking.send(player, new EntityAnimPayload(entity.getId(), a.name, a.start, a.serial));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(EntityLinks::tick);
    }

    private static void tick(MinecraftServer server) {
        if (ANIMS.isEmpty() || server.getTickCount() % 5 != 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Anim> e = it.next();
            Anim a = e.getValue();
            if (a.until > 0 && now > a.until) {
                it.remove();
                Entity ent = findLoaded(server, e.getKey());
                if (ent != null) {
                    broadcast(ent, new EntityAnimPayload(ent.getId(), "", (int) now, ++serialCounter));
                }
            }
        }
    }

    private static Entity findLoaded(MinecraftServer server, UUID id) {
        for (var world : server.getAllLevels()) {
            Entity e = world.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static void broadcast(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer p : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(p, payload);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    /** Animacion actual de una entidad ("" si ninguna). */
    public static String currentAnim(LivingEntity e) {
        if (e instanceof AnnisNpcEntity npc) {
            return npc.getEmote();
        }
        Anim a = ANIMS.get(e.getUUID());
        return a == null ? "" : a.name;
    }

    /** Pone una animacion durante holdTicks (0 = hasta que se pare). */
    public static void play(LivingEntity e, String name, int holdTicks) {
        if (e instanceof AnnisNpcEntity npc) {
            npc.playEmote(name, holdTicks <= 0 ? 20 * 60 * 60 : holdTicks);
            return;
        }
        long now = e.level().getGameTime();
        Anim a = new Anim(name == null ? "" : name, (int) now, ++serialCounter, holdTicks > 0 ? now + holdTicks : 0);
        if (a.name.isEmpty()) {
            ANIMS.remove(e.getUUID());
        } else {
            ANIMS.put(e.getUUID(), a);
        }
        broadcast(e, new EntityAnimPayload(e.getId(), a.name, a.start, a.serial));
    }

    public static void stop(LivingEntity e) {
        if (e instanceof AnnisNpcEntity npc) {
            npc.stopEmote();
            return;
        }
        if (ANIMS.remove(e.getUUID()) != null) {
            broadcast(e, new EntityAnimPayload(e.getId(), "", (int) e.level().getGameTime(), ++serialCounter));
        }
    }

    /** Aspecto de un NPC Annis listo para copiar a otra entidad. */
    public static CompoundTag lookOf(AnnisNpcEntity npc) {
        CompoundTag n = new CompoundTag();
        n.putString("Skin", npc.getSkin());
        n.putBoolean("Slim", npc.isSlim());
        n.putBoolean("Jointed", npc.isJointed());
        n.putString("Accessories", npc.getAccessories());
        n.putString("Figura", npc.getFigura());
        return n;
    }

    public static void setLook(LivingEntity e, CompoundTag look) {
        if (look == null || look.isEmpty()) {
            e.getPersistentData().remove(LOOK);
            look = new CompoundTag();
        } else {
            e.getPersistentData().put(LOOK, look.copy());
        }
        broadcast(e, new EntityLookPayload(e.getId(), look));
    }

    public static boolean hasLook(LivingEntity e) {
        CompoundTag l = getLook(e);
        return l != null && !l.isEmpty();
    }
}
