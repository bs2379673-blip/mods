package com.tunombre.infectados.mob;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;

import java.util.EnumSet;
import java.util.Map;

/**
 * Smoker (Left 4 Dead): desde lejos te atrapa con la lengua y te arrastra hasta el para asfixiarte. Mientras te
 * arrastra NO hay quick time event: dispárale (o que lo haga un amigo) para que suelte. Al morir deja una nube de humo.
 */
public class Smoker extends InfMob {
    private static final EntityDataAccessor<Integer> TONGUE = SynchedEntityData.defineId(Smoker.class, EntityDataSerializers.INT);
    private static final Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MOTIONS = Map.of(
            LivingMotions.IDLE, Anims.S_IDLE, LivingMotions.WALK, Anims.S_WALK, LivingMotions.CHASE, Anims.S_WALK);

    private int shootCd = 60;
    private int shootTick = -1;
    private float tetherDamage;
    private int noSight;
    private int coughCd = 80;

    public Smoker(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TONGUE, -1);
    }

    /** id de la entidad atrapada por la lengua (-1 = nada) */
    public int tongueTarget() { return entityData.get(TONGUE); }

    @Override
    public Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions() { return MOTIONS; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepDistanceGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected double attackRange() { return 1.8; }

    @Override
    protected void chooseAttack(LivingEntity t, double d) {
        startMove(new Move(Anims.G_SWIPE, 12, 24, 2.0, 4F, 100F), t);
        attackCd = 30;
    }

    @Override
    protected void failGrab(Player p) { }

    public Vec3 mouth() { return position().add(0, 1.62, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(0.3)); }

    private boolean clearShot(LivingEntity t) {
        Vec3 a = mouth(), b = t.position().add(0, t.getBbHeight() * 0.6, 0);
        return level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (shootCd > 0) shootCd--;
        LivingEntity t = getTarget();
        Entity tied = tongueTarget() >= 0 ? level().getEntity(tongueTarget()) : null;
        if (tied instanceof LivingEntity le) tickTongue(le);
        else if (tongueTarget() >= 0) release();
        // disparo de la lengua
        if (shootTick >= 0) {
            shootTick++;
            getNavigation().stop();
            if (t != null) getLookControl().setLookAt(t, 40, 40);
            if (shootTick == 14) {
                if (t != null && t.isAlive() && distanceTo(t) < 24 && clearShot(t)) {
                    entityData.set(TONGUE, t.getId());
                    tetherDamage = 0;
                    noSight = 0;
                    play(Anims.S_PULL);
                    Voice.SM_LENGUA.say(this, true);
                } else shootCd = 60;
            }
            if (shootTick > 24) shootTick = -1;
        } else if (t != null && tongueTarget() < 0 && shootCd <= 0 && !busy()) {
            double d = distanceTo(t);
            if (d > 4 && d < 22 && hasLineOfSight(t) && clearShot(t) && !(t instanceof Player p && Grabs.recentlyGrabbed(p))) {
                shootTick = 0;
                play(Anims.S_SHOOT);
            }
        }
        if (--coughCd <= 0) {
            coughCd = 100 + random.nextInt(140);
            Voice.SM_TOS.say(this, false);
        }
    }

    private void tickTongue(LivingEntity v) {
        getNavigation().stop();
        getLookControl().setLookAt(v, 60, 60);
        double d = distanceTo(v);
        if (!v.isAlive() || d > 28 || (v instanceof Player p && (p.isCreative() || p.isSpectator()))) { release(); return; }
        noSight = clearShot(v) ? 0 : noSight + 1;
        if (noSight > 30) { release(); return; }
        if (d > 1.7) {
            // arrastre: lo trae hacia el, puede seguir disparando
            Vec3 to = position().subtract(v.position());
            Vec3 h = new Vec3(to.x, 0, to.z).normalize().scale(0.16);
            v.setDeltaMovement(h.x, Math.min(v.getDeltaMovement().y, 0.05) + (to.y > 0.5 ? 0.08 : 0), h.z);
            v.hurtMarked = true;
            v.fallDistance = 0;
        } else {
            // asfixia
            v.setDeltaMovement(0, v.getDeltaMovement().y, 0);
            v.hurtMarked = true;
            if (tickCount % 10 == 0) v.hurt(damageSources().mobAttack(this), 2F);
        }
        if (v instanceof Player p) Grabs.mark(p);
    }

    private void release() {
        if (tongueTarget() >= 0) {
            entityData.set(TONGUE, -1);
            stop(Anims.S_PULL);
            shootCd = 120 + random.nextInt(80);
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && tongueTarget() >= 0 && !level().isClientSide) {
            tetherDamage += amount;
            boolean melee = src.getDirectEntity() == src.getEntity() && src.getEntity() != null;
            if (tetherDamage >= 8F || melee) release();     // dispararle lo obliga a soltar
        }
        return r;
    }

    @Override
    public void die(DamageSource src) {
        if (level() instanceof ServerLevel lv) {
            release();
            Voice.SM_MUERTE.say(this, true);
            Vec3 c = position().add(0, 1, 0);
            lv.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 40, 1.2, 0.8, 1.2, 0.01);
            lv.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.5F, 0.3F), 2.5F), c.x, c.y, c.z, 80, 1.5, 0.9, 1.5, 0.02);
            for (Player p : lv.getEntitiesOfClass(Player.class, getBoundingBox().inflate(3.5))) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100));
            }
        }
        super.die(src);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }

    /** se mantiene a media distancia con linea de vision; si esta muy cerca, golpea */
    static class KeepDistanceGoal extends Goal {
        private final Smoker mob;
        private int repath;

        KeepDistanceGoal(Smoker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return mob.getTarget() != null && mob.getTarget().isAlive() && !mob.busy() && mob.tongueTarget() < 0 && mob.shootTick < 0; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            double d = mob.distanceTo(t);
            mob.getLookControl().setLookAt(t, 30, 30);
            if (d < 1.9 && mob.attackCd <= 0) { mob.chooseAttack(t, d); return; }
            if (--repath > 0) return;
            repath = 10;
            if (d > 14 || !mob.hasLineOfSight(t)) mob.getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = mob.position().subtract(t.position()).normalize().scale(6).add(mob.position());
                mob.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
            } else mob.getNavigation().stop();
        }
    }
}
