package com.annis.interact;

import com.annis.interact.net.DownedPayload;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityLinks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

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
    private static final Identifier SLOW = Identifier.of(AnnisInteract.MOD_ID, "caido_quieto");
    private static final Identifier NOJUMP = Identifier.of(AnnisInteract.MOD_ID, "caido_sin_salto");

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
        return e != null && DOWNED.containsKey(e.getUuid());
    }

    public static Float yawOf(Entity e) {
        State s = DOWNED.get(e.getUuid());
        return s == null ? null : s.yaw;
    }

    /** ServerLivingEntityEvents.ALLOW_DEATH: false = no muere (queda caido). */
    public static boolean allowDeath(LivingEntity e, DamageSource source, float amount) {
        InteractConfig cfg = InteractConfig.get();
        if (finishing || !cfg.caidos || isDowned(e)) {
            return true;
        }
        boolean player = e instanceof ServerPlayerEntity;
        boolean npc = cfg.npcsCaen && e instanceof AnnisNpcEntity;
        if (!player && !npc) {
            return true;
        }
        if (player && (((ServerPlayerEntity) e).isCreative() || e.isSpectator())) {
            return true;
        }
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (e.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || e.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return true;
        }
        down(e, source);
        return false;
    }

    public static void down(LivingEntity e, DamageSource source) {
        InteractConfig cfg = InteractConfig.get();
        Sessions.endFor(e, null);
        if (e.hasVehicle()) {
            e.stopRiding();
        }
        e.removeAllPassengers();
        e.setHealth(1.0f);
        e.extinguish();
        e.setFrozenTicks(0);
        for (StatusEffectInstance fx : new ArrayList<>(e.getStatusEffects())) {
            if (fx.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
                e.removeStatusEffect(fx.getEffectType());
            }
        }
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false));
        modifier(e, true);
        float yaw = e.bodyYaw;
        State st = new State(e, Math.max(5, cfg.segundosCaido) * 20L, yaw, source);
        DOWNED.put(e.getUuid(), st);
        if (e instanceof MobEntity m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        EntityLinks.play(e, "caido", 0);
        Sessions.anchor(e, yaw);
        if (e instanceof ServerPlayerEntity p) {
            ServerPlayNetworking.send(p, new DownedPayload((int) (st.left / 20), true));
        }
        Text msg = Text.literal(Sessions.name(e) + " ha caido. Acercate y manten G para ayudarle a levantarse.")
                .formatted(Formatting.GOLD);
        for (ServerPlayerEntity p : ((ServerWorld) e.getWorld()).getPlayers()) {
            if (p != e && p.squaredDistanceTo(e) < 48 * 48) {
                p.sendMessage(msg, false);
            }
        }
    }

    private static void modifier(LivingEntity e, boolean on) {
        EntityAttributeInstance speed = e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeInstance jump = e.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(SLOW);
            if (on) {
                speed.addTemporaryModifier(new EntityAttributeModifier(SLOW, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (jump != null) {
            jump.removeModifier(NOJUMP);
            if (on) {
                jump.addTemporaryModifier(new EntityAttributeModifier(NOJUMP, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void clear(LivingEntity e) {
        DOWNED.remove(e.getUuid());
        modifier(e, false);
        Sessions.unanchor(e);
        if (e instanceof ServerPlayerEntity p) {
            ServerPlayNetworking.send(p, new DownedPayload(0, false));
        }
    }

    public static void revive(LivingEntity e, LivingEntity helper) {
        if (!isDowned(e)) {
            return;
        }
        clear(e);
        e.setHealth(Math.max(1, Math.min(e.getMaxHealth(), InteractConfig.get().vidaAlLevantar)));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
        String who = helper == null ? "" : " gracias a " + Sessions.name(helper);
        Text msg = Text.literal(Sessions.name(e) + " se levanto" + who).formatted(Formatting.GREEN);
        Sessions.say(e, msg.getString());
        Sessions.say(helper, msg.getString());
    }

    /** Muere de verdad (se acabo el tiempo, se rindio o se desconecto). */
    public static void finish(LivingEntity e) {
        State st = DOWNED.get(e.getUuid());
        if (st == null) {
            return;
        }
        Sessions.endFor(e, null);
        clear(e);
        finishing = true;
        try {
            e.timeUntilRegen = 0;
            e.damage(st.source, Float.MAX_VALUE);
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
        long tick = server.getTicks();
        for (State st : new ArrayList<>(DOWNED.values())) {
            LivingEntity e = st.entity;
            if (e.isRemoved() || !e.isAlive()) {
                clear(e);
                continue;
            }
            e.setVelocity(e.getVelocity().multiply(0, 1, 0));
            if (e instanceof MobEntity m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
            if (!Sessions.inRevive(e)) {
                st.left--;
            }
            if (e instanceof ServerPlayerEntity p) {
                if (p.isSneaking() && !Sessions.inRevive(e)) {
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
            if (tick % 10 == 0 && InteractConfig.get().ayudaAutomatica && e instanceof ServerPlayerEntity p) {
                autoHelp(st, p);
            }
        }
    }

    private static void calmMobs(LivingEntity e) {
        Box box = e.getBoundingBox().expand(32);
        for (MobEntity m : e.getWorld().getEntitiesByClass(MobEntity.class, box, m -> m.getTarget() == e)) {
            m.setTarget(null);
        }
    }

    /** NPC que te siguen y aldeanos de MCA que te quieren vienen a levantarte. */
    private static void autoHelp(State st, ServerPlayerEntity p) {
        if (Sessions.busy(p)) {
            return;
        }
        LivingEntity helper = null;
        if (st.helper != null) {
            Entity h = ((ServerWorld) p.getWorld()).getEntity(st.helper);
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
            st.helper = helper.getUuid();
            st.helperTicks = 0;
            Sessions.say(p, Sessions.name(helper) + " viene a ayudarte");
        }
        st.helperTicks += 10;
        if (!(helper instanceof MobEntity m)) {
            return;
        }
        float r = st.yaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d slot = p.getPos().add(-MathHelper.sin(r) * Layout.HELP, 0, MathHelper.cos(r) * Layout.HELP);
        double d2 = m.getPos().squaredDistanceTo(slot);
        if (d2 < 1.2 * 1.2) {
            if (Sessions.check(Interaction.HELP, m, p) == null) {
                Sessions.start(Interaction.HELP, m, p);
            }
        } else {
            m.getNavigation().startMovingTo(slot.x, slot.y, slot.z, 1.25);
        }
    }

    private static LivingEntity findHelper(ServerPlayerEntity p) {
        InteractConfig cfg = InteractConfig.get();
        Box box = p.getBoundingBox().expand(20, 6, 20);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        List<LivingEntity> list = p.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != p && e.isAlive());
        for (LivingEntity e : list) {
            if (Sessions.busy(e) || isDowned(e) || !(e instanceof MobEntity)) {
                continue;
            }
            boolean ok = false;
            if (e instanceof AnnisNpcEntity npc) {
                ok = p.getUuid().equals(npc.getOwnerUuid()) && npc.getMode() != 0;
            } else if (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(e)) {
                ok = !com.annis.interact.mca.McaBridge.isMinor(e) && !com.annis.interact.mca.McaBridge.isBusy(e)
                        && (com.annis.interact.mca.McaBridge.isPartner(e, p)
                        || com.annis.interact.mca.McaBridge.hearts(e, p) >= cfg.corazonesParaAyudar);
            }
            if (!ok) {
                continue;
            }
            double d = e.squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        if (isDowned(p)) {
            finish(p);
        }
    }

    public static void clearAll() {
        DOWNED.clear();
    }
}
