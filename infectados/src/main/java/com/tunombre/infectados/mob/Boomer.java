package com.tunombre.infectados.mob;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boomer (Left 4 Dead): gordo y lento. De cerca te vomita bilis (ciego, mareado, y todos los monstruos de alrededor van a
 * por ti un rato). Al morir REVIENTA: sangre y bilis por todos lados (la bilis tambien te marca).
 */
public class Boomer extends InfMob {
    private static final Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MOTIONS = Map.of(
            LivingMotions.IDLE, Anims.B_IDLE, LivingMotions.WALK, Anims.B_WALK, LivingMotions.CHASE, Anims.B_WALK);
    /** jugadores marcados con bilis -> tick en que termina */
    private static final Map<UUID, Long> BILED = new ConcurrentHashMap<>();

    private int vomitTick = -1;
    private int gurgleCd = 60;

    public Boomer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    public Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions() { return MOTIONS; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this, 0.9, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected double attackRange() { return 4.5; }

    @Override
    protected void chooseAttack(LivingEntity t, double d) {
        if (d > 2.2) {
            vomitTick = 0;
            startMove(new Move(Anims.B_VOMIT, 999, 42, 0, 0, 0), t);
            attackCd = 140;
        } else {
            startMove(new Move(Anims.G_SWIPE, 13, 27, 2.2, 5F, 100F), t);
            attackCd = 30;
        }
    }

    @Override
    protected void failGrab(Player p) { }

    private void tickVomit() {
        if (vomitTick < 0) return;
        vomitTick++;
        if (vomitTick == 13) Voice.BO_VOMITO.say(this, true);
        if (vomitTick >= 14 && vomitTick <= 30 && level() instanceof ServerLevel lv) {
            Vec3 f = Vec3.directionFromRotation(getXRot() * 0.3F + 15F, getYRot());
            Vec3 m = position().add(0, 1.5, 0).add(f.scale(0.4));
            for (int i = 0; i < 6; i++) {
                Vec3 p = m.add(f.scale(0.6 + random.nextDouble() * 4.0)).add((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.8);
                lv.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.6F, 0.15F), 1.6F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                lv.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
            for (Player pl : lv.getEntitiesOfClass(Player.class, getBoundingBox().inflate(5))) {
                Vec3 to = pl.getEyePosition().subtract(m);
                if (to.length() < 5.2 && to.normalize().dot(f) > 0.75) bile(pl, 200);
            }
        }
        if (vomitTick > 32) vomitTick = -1;
    }

    /** marca con bilis: ciego/mareado y los monstruos de 24 bloques van a por el */
    public static void bile(Player p, int ticks) {
        if (p.isCreative() || p.isSpectator()) return;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(ticks, 120)));
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks));
        BILED.put(p.getUUID(), p.level().getGameTime() + ticks);
        attract(p);
    }

    private static void attract(Player p) {
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), x -> x instanceof Enemy && !(x instanceof Witch))) {
            m.setTarget(p);
        }
    }

    /** cada segundo, mientras dure la bilis, vuelve a atraer a la horda (lo llama Infectados en el tick de jugador) */
    public static void tickBile(Player p) {
        Long end = BILED.get(p.getUUID());
        if (end == null) return;
        if (p.level().getGameTime() > end) { BILED.remove(p.getUUID()); return; }
        if (p.tickCount % 20 == 0) attract(p);
        if (p.tickCount % 4 == 0 && p.level() instanceof ServerLevel lv) {
            lv.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.6F, 0.15F), 1.0F), p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.5, 0.3, 0);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        tickVomit();
        if (--gurgleCd <= 0) {
            gurgleCd = 80 + random.nextInt(120);
            Voice.BO_GORGOTEO.say(this, false);
        }
    }

    @Override
    public void die(DamageSource src) {
        if (level() instanceof ServerLevel lv) {
            // revienta
            Voice.BO_EXPLOSION.say(this, true);
            Vec3 c = position().add(0, 1.1, 0);
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), c.x, c.y, c.z, 160, 1.0, 0.8, 1.0, 0.45);
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()), c.x, c.y, c.z, 120, 1.0, 0.8, 1.0, 0.4);
            lv.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.6F, 0.15F), 2.5F), c.x, c.y, c.z, 80, 2, 1, 2, 0.05);
            lv.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4))) {
                if (e == this) continue;
                Vec3 away = e.position().subtract(position()).normalize();
                e.knockback(0.7, -away.x, -away.z);
                if (e instanceof Player p) bile(p, 240);
            }
        }
        super.die(src);
        if (!level().isClientSide) discard();     // no queda cuerpo: revento
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
}
