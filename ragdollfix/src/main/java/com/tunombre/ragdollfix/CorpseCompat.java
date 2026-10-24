package com.tunombre.ragdollfix;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibilidad Corpse + Ragdollified, todo por reflexion (ninguno de los dos es dependencia dura).
 * Corpse deja una entidad cadaver con el inventario del muerto; Ragdollified dibuja el cuerpo con fisicas. Para tener las dos cosas:
 *  - cliente: el cadaver de Corpse no se dibuja mientras exista el ragdoll de ese jugador (el ragdoll es el cuerpo visible) y el ragdoll
 *    se hace persistente mientras el cadaver exista; cuando el cadaver desaparece (lo vaciaron o se vencio) se borra tambien el ragdoll.
 *  - servidor: el cadaver (que sigue siendo quien abre el inventario con clic derecho) se mueve hasta donde esta el ragdoll, asi la
 *    hitbox queda sobre el cuerpo que se ve.
 */
public final class CorpseCompat {
    static final String CORPSE = "de.maxhenkel.corpse.entities.CorpseEntity";
    private static final double ALTURA = 0.1;   // el ancla del ragdoll queda a esta altura sobre el suelo, el cadaver se apoya mas abajo

    private CorpseCompat() { }

    static boolean isCorpse(Entity e) { return e != null && e.getClass().getName().equals(CORPSE); }

    @SuppressWarnings("unchecked")
    static UUID owner(Entity corpse) {
        try {
            Optional<UUID> o = (Optional<UUID>) corpse.getClass().getMethod("getCorpseUUID").invoke(corpse);
            return o.orElse(null);
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------------------------------------ servidor
    public static final class Server {
        private static final Map<Integer, Entity> CORPSES = new ConcurrentHashMap<>();
        private static Method near, victim, anchor;
        private static boolean tried, broken;

        @SubscribeEvent
        public void onJoin(EntityJoinLevelEvent e) {
            if (!e.getLevel().isClientSide && isCorpse(e.getEntity())) CORPSES.put(e.getEntity().getId(), e.getEntity());
        }

        @SubscribeEvent
        public void onLeave(EntityLeaveLevelEvent e) {
            if (!e.getLevel().isClientSide) CORPSES.remove(e.getEntity().getId());
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || broken || CORPSES.isEmpty() || e.getServer().getTickCount() % 4 != 0) return;
            try {
                if (!tried) {
                    tried = true;
                    Class<?> mgr = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager");
                    Class<?> body = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager$RetainedBody");
                    near = mgr.getMethod("retainedBodiesNear", net.minecraft.resources.ResourceKey.class, Vec3.class, double.class);
                    victim = body.getMethod("victim");
                    anchor = body.getMethod("anchor");
                }
            } catch (Throwable t) { broken = true; return; }   // no hay Ragdollified (o cambio su API): no hace nada
            for (Entity c : CORPSES.values()) {
                if (c.isRemoved()) continue;
                UUID id = owner(c);
                if (id == null) continue;
                try {
                    List<?> bodies = (List<?>) near.invoke(null, c.level().dimension(), c.position(), 96.0);
                    for (Object b : bodies) {
                        if (!id.equals(victim.invoke(b))) continue;
                        Vec3 a = (Vec3) anchor.invoke(b);
                        if (a == null || a.distanceToSqr(c.getX(), c.getY() + ALTURA, c.getZ()) < 0.04) break;
                        c.setPos(a.x, a.y - ALTURA, a.z);
                        break;
                    }
                } catch (Throwable ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ cliente
    public static final class Client {
        private static Method ids, playerUuid, setHidden, getHandle, remove;
        private static boolean tried, broken;
        /** cadaver (id de entidad) -> id del ragdoll que lo representa */
        private static final Map<Integer, Integer> LINK = new ConcurrentHashMap<>();

        private static boolean init() {
            if (broken) return false;
            if (tried) return true;
            tried = true;
            try {
                Class<?> api = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedApi");
                ids = api.getMethod("getRagdollIds");
                playerUuid = api.getMethod("getPlayerUUID", int.class);
                setHidden = api.getMethod("setEntityHidden", Entity.class, boolean.class);
                getHandle = api.getMethod("getHandle", int.class);
                remove = api.getMethod("remove", int.class);
                return true;
            } catch (Throwable t) { broken = true; return false; }
        }

        @SuppressWarnings("unchecked")
        private static int ragdollOf(UUID owner) throws Exception {
            for (Integer rid : (java.util.Set<Integer>) ids.invoke(null)) {
                Optional<UUID> u = (Optional<UUID>) playerUuid.invoke(null, rid);
                if (u.isPresent() && u.get().equals(owner)) return rid;
            }
            return -1;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.level.getGameTime() % 5 != 0 || !init()) return;
            for (Entity c : mc.level.entitiesForRendering()) {
                if (!isCorpse(c)) continue;
                UUID owner = owner(c);
                if (owner == null) continue;
                try {
                    int rid = ragdollOf(owner);
                    if (rid >= 0) {
                        setHidden.invoke(null, c, true);
                        LINK.put(c.getId(), rid);
                        Optional<?> h = (Optional<?>) getHandle.invoke(null, rid);
                        if (h.isPresent()) h.get().getClass().getMethod("setPersistent", boolean.class).invoke(h.get(), true);
                    } else if (LINK.remove(c.getId()) != null) {
                        setHidden.invoke(null, c, false);   // el ragdoll ya no existe: se ve el cuerpo de Corpse
                    }
                } catch (Throwable ignored) { }
            }
        }

        @SubscribeEvent
        public void onLeave(EntityLeaveLevelEvent e) {
            if (!e.getLevel().isClientSide || !isCorpse(e.getEntity())) return;
            Integer rid = LINK.remove(e.getEntity().getId());
            if (rid == null || !init()) return;
            try { remove.invoke(null, rid); } catch (Throwable ignored) { }   // el cadaver ya no existe: el cuerpo tampoco
        }
    }
}
