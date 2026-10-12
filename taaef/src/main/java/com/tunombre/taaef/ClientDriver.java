package com.tunombre.taaef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.event.TickEvent;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * En cada cliente, para cada jugador visible: mira que esta haciendo segun TAA (estado que TAA ya sincroniza entre
 * jugadores) y reproduce la animacion de Epic Fight que toca. Asi todos ven a todos sin paquetes nuevos.
 */
public final class ClientDriver {
    private ClientDriver() { }

    private static final class St {
        AnimationManager.AnimationAccessor<StaticAnimation> cur;
        boolean loop, lastKick, lastAmmo;
        long endAt;
    }

    private static final Map<Integer, St> STATES = new HashMap<>();
    private static final Set<Integer> KICK_NOW = new HashSet<>();
    private static boolean warned;

    /** TAA empieza una patada (llamado desde su propio codigo): la nuestra arranca en el mismo instante */
    public static void kickStarted(int entityId) {
        KICK_NOW.add(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) {
            try { update(p, p == mc.player); } catch (Throwable ignored) { }
        }
    }

    /** abriste una puerta con la mano (F): lo ves tu y se avisa a los demas */
    public static void localDoorPush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        playOnce(mc.player, Anims.DOOR_PUSH);
        try { Net.CH.sendToServer(new Net.DoorPush()); } catch (Throwable ignored) { }
    }

    static void remoteFx(int entityId, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && kind == 0 && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) playOnce(p, Anims.DOOR_PUSH);
    }

    private static void playOnce(AbstractClientPlayer p, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
        if (patch != null && patch.getClientAnimator() != null) patch.getClientAnimator().playAnimation(a, 0.08F);
    }

    static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Taa.present()) { STATES.clear(); return; }
        Set<Integer> seen = new HashSet<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            seen.add(p.getId());
            try {
                update(p, p == mc.player);
            } catch (Throwable t) {
                if (!warned) { warned = true; TaaEF.LOG.warn("TAA Epic Fight: fallo animando a {}: {}", p.getName().getString(), t.toString()); }
            }
        }
        STATES.keySet().retainAll(seen);
    }

    private static void update(AbstractClientPlayer p, boolean local) {
        St s = STATES.computeIfAbsent(p.getId(), k -> new St());
        long now = System.currentTimeMillis();
        String kind = Taa.gunKind(p.getMainHandItem());
        boolean gun = kind != null, pistol = "PISTOL".equals(kind);

        boolean prone = Taa.prone(p);
        boolean kick = Taa.kick(p) || KICK_NOW.remove(p.getId());
        boolean ammo = gun && Taa.ammoCheck(p);
        boolean kickEdge = kick && !s.lastKick, ammoEdge = ammo && !s.lastAmmo;
        s.lastKick = kick;
        s.lastAmmo = ammo;

        AnimationManager.AnimationAccessor<StaticAnimation> want = null;
        boolean loop = true;
        long dur = 0;
        if (prone) {
            // cuerpo a tierra como en los shooters: todo el cuerpo gira con la mira para poder apuntar a cualquier lado
            p.yBodyRot = p.getYHeadRot();
            boolean moving = (p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo) > 1.0E-4;
            want = moving ? (pistol ? Anims.PRONE_CRAWL_PISTOL : Anims.PRONE_CRAWL_RIFLE) : (pistol ? Anims.PRONE_PISTOL : Anims.PRONE_RIFLE);
        } else if (kickEdge) {
            want = pistol || !gun ? Anims.KICK_PISTOL : Anims.KICK_RIFLE; loop = false; dur = 950;
        } else if (ammoEdge) {
            want = pistol ? Anims.AMMO_CHECK_PISTOL : Anims.AMMO_CHECK_RIFLE; loop = false; dur = pistol ? 1500 : 1800;
        } else if (gun && Taa.highReady(p, local)) {
            want = pistol ? Anims.HIGH_READY_PISTOL : Anims.HIGH_READY_RIFLE;
        } else if (gun && !Taa.kickRaise(p) && Taa.wall(p, local)) {
            want = pistol ? Anims.WALL_PISTOL : Anims.WALL_RIFLE;
        } else if (gun && Taa.gunRun(p)) {
            want = pistol ? Anims.GUN_RUN_PISTOL : Anims.GUN_RUN_RIFLE;
        }

        // una animacion de una sola vez (patada, municion) se deja terminar, salvo que se tire cuerpo a tierra
        boolean oneShotRunning = s.cur != null && !s.loop && now < s.endAt;
        if (oneShotRunning && !prone) return;
        if (want == s.cur && (s.loop || oneShotRunning)) return;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
        if (patch == null || patch.getClientAnimator() == null) return;
        if (s.cur != null && (s.loop || oneShotRunning)) patch.getClientAnimator().stopPlaying(s.cur);
        s.cur = want;
        s.loop = loop;
        s.endAt = now + dur;
        if (want != null) patch.getClientAnimator().playAnimation(want, 0.15F);
    }
}
