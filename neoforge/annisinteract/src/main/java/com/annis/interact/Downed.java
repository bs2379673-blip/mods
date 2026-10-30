package com.annis.interact;

import com.annis.interact.net.DownedPayload;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityLinks;
import com.annis.npc.compat.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caidos: en vez de morir, el jugador (o NPC) queda en el suelo un rato. Otro jugador, un NPC que
 * lo sigue o un aldeano de MCA que lo quiere puede levantarlo; si nadie llega, muere.
 */
public final class Downed {
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "caido_quieto");
    private static final ResourceLocation NOJUMP = ResourceLocation.fromNamespaceAndPath(AnnisInteract.MOD_ID, "caido_sin_salto");

    static final class State {
        final LivingEntity entity;
        long left;
        final float yaw;
        final DamageSource source;
        int giveUp;
        UUID helper;
        int helperTicks;

        State(LivingEntity entity, long left, float yaw, DamageSource source) {
            this.entity = entity;
            this.left = left;
            this.yaw = yaw;
            this.source = source;
        }
    }

    private static final Map<UUID, State> DOWNED = new HashMap<>();
    private static boolean finishing;

    private Downed() {
    }

    public static boolean isDowned(Entity e) {
        return e != null && DOWNED.containsKey(e.getUUID());
    }

    public static Float yawOf(Entity e) {
        State s = DOWNED.get(e.getUUID());
        return s == null ? null : s.yaw;
    }

    /** ServerLivingEntityEvents.ALLOW_DEATH: false = no muere (queda caido). */
    public static boolean allowDeath(LivingEntity e, DamageSource source, float amount) {
        InteractConfig cfg = InteractConfig.get();
        if (finishing || !cfg.caidos || isDowned(e)) {
            return true;
        }
        boolean player = e instanceof ServerPlayer;
        boolean npc = cfg.npcsCaen && e instanceof AnnisNpcEntity;
        if (!player && !npc) {
            return true;
        }
        if (player && (((ServerPlayer) e).isCreative() || e.isSpectator())) {
            return true;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (e.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || e.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return true;
        }
        down(e, source);
        return false;
    }

    public static void down(LivingEntity e, DamageSource source) {
        InteractConfig cfg = InteractConfig.get();
        Sessions.endFor(e, null);
        if (e.isPassenger()) {
            e.stopRiding();
        }
        e.ejectPassengers();
        e.setHealth(1.0f);
        e.clearFire();
        e.setTicksFrozen(0);
        for (MobEffectInstance fx : new ArrayList<>(e.getActiveEffects())) {
            if (fx.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                e.removeEffect(fx.getEffect());
            }
        }
        e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        modifier(e, true);
        float yaw = e.yBodyRot;
        State st = new State(e, Math.max(5, cfg.segundosCaido) * 20L, yaw, source);
        DOWNED.put(e.getUUID(), st);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        EntityLinks.play(e, "caido", 0);
        Sessions.anchor(e, yaw);
        if (e instanceof ServerPlayer p) {
            ServerPlayNetworking.send(p, new DownedPayload((int) (st.left / 20), true));
        }
        Component msg = Component.literal(Sessions.name(e) + " ha caido. Acercate y manten G para ayudarle a levantarse.")
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : ((ServerLevel) e.level()).players()) {
            if (p != e && p.distanceToSqr(e) < 48 * 48) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    private static void modifier(LivingEntity e, boolean on) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = e.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(SLOW);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(SLOW, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (jump != null) {
            jump.removeModifier(NOJUMP);
            if (on) {
                jump.addTransientModifier(new AttributeModifier(NOJUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void clear(LivingEntity e) {
        DOWNED.remove(e.getUUID());
        modifier(e, false);
        Sessions.unanchor(e);
        // sin esto, al reaparecer seguia viendose tumbado
        EntityLinks.stop(e);
        if (e instanceof ServerPlayer p) {
            ServerPlayNetworking.send(p, new DownedPayload(0, false));
        }
    }

    public static void revive(LivingEntity e, LivingEntity helper) {
        if (!isDowned(e)) {
            return;
        }
        clear(e);
        e.setHealth(Math.max(1, Math.min(e.getMaxHealth(), InteractConfig.get().vidaAlLevantar)));
        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        String who = helper == null ? "" : " gracias a " + Sessions.name(helper);
        Component msg = Component.literal(Sessions.name(e) + " se levanto" + who).withStyle(ChatFormatting.GREEN);
        Sessions.say(e, msg.getString());
        Sessions.say(helper, msg.getString());
    }

    /** Muere de verdad (se acabo el tiempo, se rindio o se desconecto). */
    public static void finish(LivingEntity e) {
        State st = DOWNED.get(e.getUUID());
        if (st == null) {
            return;
        }
        Sessions.endFor(e, null);
        clear(e);
        finishing = true;
        try {
            e.invulnerableTime = 0;
            e.hurt(st.source, Float.MAX_VALUE);
            if (e.isAlive()) {
                e.kill();
            }
        } finally {
            finishing = false;
        }
    }

    public static void tick(MinecraftServer server) {
        if (DOWNED.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        for (State st : new ArrayList<>(DOWNED.values())) {
            LivingEntity e = st.entity;
            if (e.isRemoved() || !e.isAlive()) {
                clear(e);
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().multiply(0, 1, 0));
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
            if (!Sessions.inRevive(e)) {
                st.left--;
            }
            if (e instanceof ServerPlayer p) {
                if (p.isShiftKeyDown() && !Sessions.inRevive(e)) {
                    st.giveUp++;
                    if (st.giveUp % 20 == 0 && st.giveUp < 60) {
                        Sessions.say(p, "Te rindes en " + (3 - st.giveUp / 20) + "...");
                    }
                } else {
                    st.giveUp = 0;
                }
                if (tick % 20 == 0) {
                    ServerPlayNetworking.send(p, new DownedPayload((int) Math.max(0, st.left / 20), true));
                }
            }
            if (st.left <= 0 || st.giveUp >= 60) {
                finish(e);
                continue;
            }
            if (tick % 10 == 0 && InteractConfig.get().monstruosIgnoranCaidos) {
                calmMobs(e);
            }
            if (tick % 10 == 0 && InteractConfig.get().ayudaAutomatica && e instanceof ServerPlayer p) {
                autoHelp(st, p);
            }
        }
    }

    private static void calmMobs(LivingEntity e) {
        AABB box = e.getBoundingBox().inflate(32);
        for (Mob m : e.level().getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == e)) {
            m.setTarget(null);
        }
    }

    /** NPC que te siguen y aldeanos de MCA que te quieren vienen a levantarte. */
    private static void autoHelp(State st, ServerPlayer p) {
        if (Sessions.busy(p)) {
            return;
        }
        LivingEntity helper = null;
        if (st.helper != null) {
            Entity h = ((ServerLevel) p.level()).getEntity(st.helper);
            if (h instanceof LivingEntity l && l.isAlive() && !Sessions.busy(l) && st.helperTicks < 20 * 25) {
                helper = l;
            } else {
                st.helper = null;
            }
        }
        if (helper == null) {
            helper = findHelper(p);
            if (helper == null) {
                return;
            }
            st.helper = helper.getUUID();
            st.helperTicks = 0;
            Sessions.say(p, Sessions.name(helper) + " viene a ayudarte");
        }
        st.helperTicks += 10;
        if (!(helper instanceof Mob m)) {
            return;
        }
        float r = st.yaw * Mth.DEG_TO_RAD;
        Vec3 slot = p.position().add(-Mth.sin(r) * Layout.HELP, 0, Mth.cos(r) * Layout.HELP);
        double d2 = m.position().distanceToSqr(slot);
        if (d2 < 1.2 * 1.2) {
            if (Sessions.check(Interaction.HELP, m, p) == null) {
                Sessions.start(Interaction.HELP, m, p);
            }
        } else {
            m.getNavigation().moveTo(slot.x, slot.y, slot.z, 1.25);
        }
    }

    private static LivingEntity findHelper(ServerPlayer p) {
        InteractConfig cfg = InteractConfig.get();
        AABB box = p.getBoundingBox().inflate(20, 6, 20);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        List<LivingEntity> list = p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive());
        for (LivingEntity e : list) {
            if (Sessions.busy(e) || isDowned(e) || !(e instanceof Mob)) {
                continue;
            }
            boolean ok = false;
            if (e instanceof AnnisNpcEntity npc) {
                ok = p.getUUID().equals(npc.getOwnerUuid()) && npc.getMode() != 0;
            } else if (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(e)) {
                ok = !com.annis.interact.mca.McaBridge.isMinor(e) && !com.annis.interact.mca.McaBridge.isBusy(e)
                        && (com.annis.interact.mca.McaBridge.isPartner(e, p)
                        || com.annis.interact.mca.McaBridge.hearts(e, p) >= cfg.corazonesParaAyudar);
            }
            if (!ok) {
                continue;
            }
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Al reaparecer (nueva entidad, misma id): nada de caido, ni animacion ni anclaje. */
    public static void onRespawn(ServerPlayer oldP, ServerPlayer newP) {
        DOWNED.remove(oldP.getUUID());
        modifier(newP, false);
        Sessions.unanchor(newP);
        EntityLinks.play(newP, "", 0);
        ServerPlayNetworking.send(newP, new DownedPayload(0, false));
        ServerPlayNetworking.send(newP, new com.annis.interact.net.SessionPayload(""));
    }

    /** Murio por otra cosa estando caido (lo remataron). */
    public static void onDeath(LivingEntity e) {
        if (isDowned(e)) {
            Sessions.endFor(e, null);
            clear(e);
        }
    }

    public static void onDisconnect(ServerPlayer p) {
        if (isDowned(p)) {
            finish(p);
        }
    }

    public static void clearAll() {
        DOWNED.clear();
    }
}
