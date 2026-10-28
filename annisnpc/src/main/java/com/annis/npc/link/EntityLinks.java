package com.annis.npc.link;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

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
    public static final AttachmentType<NbtCompound> LOOK =
            AttachmentRegistry.createPersistent(Identifier.of(AnnisNpc.MOD_ID, "look"), NbtCompound.CODEC);

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
            NbtCompound look = living.getAttached(LOOK);
            if (look != null && !look.isEmpty()) {
                ServerPlayNetworking.send(player, new EntityLookPayload(entity.getId(), look));
            }
            Anim a = ANIMS.get(entity.getUuid());
            if (a != null) {
                ServerPlayNetworking.send(player, new EntityAnimPayload(entity.getId(), a.name, a.start, a.serial));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(EntityLinks::tick);
    }

    private static void tick(MinecraftServer server) {
        if (ANIMS.isEmpty() || server.getTicks() % 5 != 0) {
            return;
        }
        long now = server.getOverworld().getTime();
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
        for (var world : server.getWorlds()) {
            Entity e = world.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static void broadcast(Entity entity, CustomPayload payload) {
        for (ServerPlayerEntity p : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(p, payload);
        }
        if (entity instanceof ServerPlayerEntity self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    /** Animacion actual de una entidad ("" si ninguna). */
    public static String currentAnim(LivingEntity e) {
        if (e instanceof AnnisNpcEntity npc) {
            return npc.getEmote();
        }
        Anim a = ANIMS.get(e.getUuid());
        return a == null ? "" : a.name;
    }

    /** Pone una animacion durante holdTicks (0 = hasta que se pare). */
    public static void play(LivingEntity e, String name, int holdTicks) {
        if (e instanceof AnnisNpcEntity npc) {
            npc.playEmote(name, holdTicks <= 0 ? 20 * 60 * 60 : holdTicks);
            return;
        }
        long now = e.getWorld().getTime();
        Anim a = new Anim(name == null ? "" : name, (int) now, ++serialCounter, holdTicks > 0 ? now + holdTicks : 0);
        if (a.name.isEmpty()) {
            ANIMS.remove(e.getUuid());
        } else {
            ANIMS.put(e.getUuid(), a);
        }
        broadcast(e, new EntityAnimPayload(e.getId(), a.name, a.start, a.serial));
    }

    public static void stop(LivingEntity e) {
        if (e instanceof AnnisNpcEntity npc) {
            npc.stopEmote();
            return;
        }
        if (ANIMS.remove(e.getUuid()) != null) {
            broadcast(e, new EntityAnimPayload(e.getId(), "", (int) e.getWorld().getTime(), ++serialCounter));
        }
    }

    /** Aspecto de un NPC Annis listo para copiar a otra entidad. */
    public static NbtCompound lookOf(AnnisNpcEntity npc) {
        NbtCompound n = new NbtCompound();
        n.putString("Skin", npc.getSkin());
        n.putBoolean("Slim", npc.isSlim());
        n.putBoolean("Jointed", npc.isJointed());
        n.putString("Accessories", npc.getAccessories());
        n.putString("Figura", npc.getFigura());
        return n;
    }

    public static void setLook(LivingEntity e, NbtCompound look) {
        if (look == null || look.isEmpty()) {
            e.removeAttached(LOOK);
            look = new NbtCompound();
        } else {
            e.setAttached(LOOK, look.copy());
        }
        broadcast(e, new EntityLookPayload(e.getId(), look));
    }

    public static boolean hasLook(LivingEntity e) {
        NbtCompound l = e.getAttached(LOOK);
        return l != null && !l.isEmpty();
    }
}
