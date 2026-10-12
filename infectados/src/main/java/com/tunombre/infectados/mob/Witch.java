package com.tunombre.infectados.mob;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Witch (Left 4 Dead). Solo la pone un admin (/summon infectados:witch). Esta sentada llorando. NO la molestes:
 * acercarte, correr cerca, apuntarle con una luz, disparar o hacer explotar algo cerca, o tocarla, la va enfadando
 * (se nota: deja de llorar, levanta la cabeza y gruñe). Si se enfada del todo, GRITA y va a por quien la molesto,
 * rapidisima y con zarpazos brutales. Cuando acaba con esa persona (o se cansa) se va corriendo y desaparece.
 * Lo mejor: apagar la luz, ir agachado y rodearla... o simplemente irse.
 */
public class Witch extends InfMob {
    public static final byte CRY = 0, ALERT = 1, SCREAM = 2, RAGE = 3, FLEE = 4;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Witch.class, EntityDataSerializers.BYTE);
    private static final Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MOTIONS = Map.of(
            LivingMotions.IDLE, Anims.W_CRY, LivingMotions.KNEEL, Anims.W_ALERT, LivingMotions.WALK, Anims.W_RUN, LivingMotions.CHASE, Anims.W_RUN);

    private final Map<UUID, Float> anger = new HashMap<>();
    private UUID victim;
    private int stateTicks;
    private int cryCd;

    public Witch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, CRY);
    }

    public byte state() { return entityData.get(STATE); }

    private void setState(byte s) {
        entityData.set(STATE, s);
        stateTicks = 0;
    }

    @Override
    public Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions() { return MOTIONS; }

    @Override
    public LivingMotion forcedMotion() {
        byte s = state();
        if (s == CRY) return LivingMotions.IDLE;
        if (s == ALERT) return LivingMotions.KNEEL;
        return null;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this, 1.0, 1.0) {
            @Override public boolean canUse() { return state() == RAGE && super.canUse(); }
        });
    }

    @Override
    protected double attackRange() { return 2.3; }

    @Override
    protected void chooseAttack(LivingEntity t, double d) {
        startMove(new Move(Anims.W_SLASH, 8, 15, 2.6, 18F, 120F), t);
        attackCd = 6;
    }

    @Override
    protected void failGrab(Player p) { }

    // ------------------------------------------------------------------ enfado ----
    /** algo hizo ruido cerca (disparo, explosion): lo llama Infectados desde los eventos del juego */
    public void noise(Vec3 where, Entity source, float amount) {
        if (state() > ALERT) return;
        if (where.distanceTo(position()) > 22) return;
        Player p = source instanceof Player pl ? pl : level().getNearestPlayer(this, 24);
        if (p != null) addAnger(p, amount);
    }

    private void addAnger(Player p, float a) {
        if (p.isCreative() || p.isSpectator()) return;
        anger.merge(p.getUUID(), a, Float::sum);
    }

    private void tickAnger() {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(16))) {
            if (p.isCreative() || p.isSpectator()) continue;
            double d = distanceTo(p);
            float a = (float) ((16 - d) / 16.0) * 0.45F;
            if (d < 5) a += 1.4F;
            if (d < 2.5) a += 4F;
            if (p.isSprinting()) a *= 2.5F;
            if (p.isShiftKeyDown()) a *= 0.25F;
            if (holdsLight(p) && lookingAtMe(p)) a += 1.6F;
            addAnger(p, a);
        }
        anger.replaceAll((k, v) -> Math.max(0F, v - 0.25F));
        UUID top = null;
        float best = 0;
        for (var e : anger.entrySet()) if (e.getValue() > best) { best = e.getValue(); top = e.getKey(); }
        if (best >= 100F) { startle(top); return; }
        if (state() == CRY && best > 35F) setState(ALERT);
        else if (state() == ALERT && best < 20F) setState(CRY);
        if (state() == ALERT && top != null) {
            Player p = level().getPlayerByUUID(top);
            if (p != null) getLookControl().setLookAt(p, 20, 20);
        }
    }

    private static boolean holdsLight(Player p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.is(Items.TORCH) || s.is(Items.LANTERN) || s.is(Items.SOUL_TORCH) || s.is(Items.SOUL_LANTERN) || s.is(Items.GLOWSTONE)) return true;
            String id = s.getItem().builtInRegistryHolder().key().location().getPath();
            if (id.contains("flashlight") || id.contains("linterna") || id.contains("torch")) return true;
        }
        return false;
    }

    private boolean lookingAtMe(Player p) {
        Vec3 to = position().add(0, 0.6, 0).subtract(p.getEyePosition()).normalize();
        return p.getViewVector(1F).dot(to) > 0.92;
    }

    private void startle(UUID who) {
        victim = who;
        setState(SCREAM);
        play(Anims.W_SCREAM);
        Voice.WI_GRITO.say(this, true);
        anger.clear();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide && src.getEntity() instanceof Player p && state() <= ALERT) startle(p.getUUID());
        return super.hurt(src, amount * (state() <= ALERT ? 1F : 0.8F));
    }

    // ------------------------------------------------------------------ tick ----
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        stateTicks++;
        byte s = state();
        if (s == CRY || s == ALERT) {
            getNavigation().stop();
            setTarget(null);
            tickAnger();
            if (--cryCd <= 0) {
                cryCd = s == CRY ? 70 + random.nextInt(50) : 40;
                (s == CRY ? Voice.WI_LLANTO : Voice.WI_GRUNIDO).say(this, true);
            }
        } else if (s == SCREAM) {
            getNavigation().stop();
            if (stateTicks > 20) setState(RAGE);
        } else if (s == RAGE) {
            Player v = victim == null ? null : level().getPlayerByUUID(victim);
            if (v == null || !v.isAlive() || v.isCreative() || v.isSpectator() || stateTicks > 900) {
                setState(FLEE);
                setTarget(null);
            } else {
                setTarget(v);
                if (stateTicks % 30 == 0) Voice.WI_GRITO.say(this, false);
            }
        } else if (s == FLEE) {
            if (stateTicks == 1 || getNavigation().isDone()) {
                Vec3 away = Vec3.directionFromRotation(0, random.nextFloat() * 360F).scale(20).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            }
            if (stateTicks > 100) {
                if (level() instanceof ServerLevel lv) lv.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.02);
                discard();
            }
        }
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return state() > ALERT; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("WitchState", state() <= ALERT ? CRY : state());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        byte s = tag.getByte("WitchState");
        entityData.set(STATE, s == RAGE || s == SCREAM ? FLEE : s);
    }
}
