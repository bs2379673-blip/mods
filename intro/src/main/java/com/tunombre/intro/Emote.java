package com.tunombre.intro;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deja a un jugador congelado en un emote de Epic Fight : Dancing (por ejemplo "death_emote", el que lo deja tirado).
 * El servidor lo repite justo antes de que termine para que no se corte nunca, lo mantiene en su sitio y le cierra cualquier menu
 * (como el de emotes) para que no pueda cambiarlo. Solo se toca desde Director cuando Epic Fight esta cargado.
 */
final class Emote {
    private static final class Act {
        ResourceLocation id;
        int period, next;
        double x, y, z;
    }

    private static final Map<UUID, Act> ACTIVE = new ConcurrentHashMap<>();

    private static final String[] PATHS = {
            "pla/idle_break", "m3tte_emote/lay_relax_emote", "m3tte_emote/one_arm_lay_emote", "m3tte_emote/salute_left_hand_emote",
            "m3tte_emote/sit_no_weapon_emote", "m3tte_emote/sorrow_emote", "m3tte_emote/surrender_emote", "pugilist_steve/lay_emote",
            "pugilist_steve/push_up_emote", "pugilist_steve/sit_emote", "pugilist_steve/slight_emote", "pugilist_steve/death_emote",
            "pugilist_steve/funny_emote", "pugilist_steve/attention_emote", "pugilist_steve/flapping_emote", "pugilist_steve/fun_jump_emote",
            "pugilist_steve/jump_emote", "pugilist_steve/prone_emote", "pugilist_steve/salute_emote", "pugilist_steve/dance_1",
            "pugilist_steve/dance_2", "pugilist_steve/dance_3", "pugilist_steve/dance_4", "pugilist_steve/dance_5", "pugilist_steve/dance_6",
            "pugilist_steve/dance_7", "pugilist_steve/dance_8", "pugilist_steve/dance_9", "pugilist_steve/dance_10", "pugilist_steve/dance_11",
            "pugilist_steve/piglin_celebrate_emote", "pugilist_steve/nl_bow_emote", "pugilist_steve/nl_bowing_emote",
            "pugilist_steve/nl_goad_emote", "pugilist_steve/nl_lol_emote", "pugilist_steve/nl_wave_emote"};

    private Emote() { }

    static List<String> names() {
        List<String> l = new ArrayList<>();
        for (String p : PATHS) l.add(p.substring(p.lastIndexOf('/') + 1));
        return l;
    }

    /** "death_emote" -> efdancing:biped/pugilist_steve/death_emote ; tambien acepta un id completo */
    static ResourceLocation resolve(String s) {
        if (s.contains(":")) return new ResourceLocation(s);
        for (String p : PATHS) if (p.endsWith("/" + s)) return new ResourceLocation("efdancing", "biped/" + p);
        return null;
    }

    private static LivingEntityPatch<?> patch(ServerPlayer p) {
        return EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
    }

    static boolean start(ServerPlayer p, ResourceLocation id) {
        AnimationManager.AnimationAccessor<StaticAnimation> a = AnimationManager.byKey(id);
        LivingEntityPatch<?> lp = patch(p);
        if (a == null || a.isEmpty() || lp == null) return false;
        Act act = new Act();
        act.id = id;
        act.period = Math.max(8, Math.round(a.get().getTotalTime() * 20F) - 3);
        act.next = p.server.getTickCount() + act.period;
        act.x = p.getX(); act.y = p.getY(); act.z = p.getZ();
        ACTIVE.put(p.getUUID(), act);
        lp.playAnimationSynchronized(a, 0.1F);
        return true;
    }

    static void stop(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) == null) return;
        LivingEntityPatch<?> lp = patch(p);
        if (lp == null) return;
        AnimationManager.AnimationAccessor<StaticAnimation> out = AnimationManager.byKey(new ResourceLocation("efdancing", "biped/pla/idle_break"));
        if (out != null && !out.isEmpty()) lp.playAnimationSynchronized(out, 0.1F);
        else lp.playAnimationSynchronized(Animations.EMPTY_ANIMATION, 0.1F);
    }

    static boolean active(UUID id) { return ACTIVE.containsKey(id); }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        int now = server.getTickCount();
        for (Map.Entry<UUID, Act> en : ACTIVE.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) continue;
            Act a = en.getValue();
            if (p.containerMenu != p.inventoryMenu) p.closeContainer();   // el menu de emotes u otro: no puede cambiar el emote
            double dx = p.getX() - a.x, dz = p.getZ() - a.z;
            if (dx * dx + dz * dz > 0.36 || p.getY() > a.y + 1.5) {
                p.teleportTo(p.serverLevel(), a.x, p.getY() > a.y + 1.5 ? a.y : p.getY(), a.z, p.getYRot(), p.getXRot());
            }
            if (now >= a.next) {
                AnimationManager.AnimationAccessor<StaticAnimation> an = AnimationManager.byKey(a.id);
                LivingEntityPatch<?> lp = patch(p);
                if (an != null && !an.isEmpty() && lp != null) lp.playAnimationSynchronized(an, 0.05F);
                a.next = now + a.period;
            }
        }
    }
}
