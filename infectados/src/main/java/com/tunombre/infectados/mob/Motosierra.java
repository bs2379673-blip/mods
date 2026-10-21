package com.tunombre.infectados.mob;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;

import java.util.Map;

/**
 * El de la motosierra (jefe). Grande, lento, mucha vida, barra de jefe. Barridos y golpes de arriba abajo; y su agarre:
 * te sujeta del cuello con la sierra en alto -> quick time event mas dificil; si fallas, TE DECAPITA.
 */
public class Motosierra extends InfMob {
    private static final Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MOTIONS = Map.of(
            LivingMotions.IDLE, Anims.M_IDLE, LivingMotions.WALK, Anims.M_WALK, LivingMotions.CHASE, Anims.M_RUN);

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Dr. Salvador"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private LivingEntity lastTarget;
    private int grabCd = 200;
    private int voiceCd = 100;

    public Motosierra(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D);
    }

    @Override
    public Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions() { return MOTIONS; }

    @Override protected boolean isBoss() { return true; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this, 0.9, 1.25));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected double attackRange() { return 2.9; }

    @Override
    protected void chooseAttack(LivingEntity t, double d) {
        if (t instanceof Player p && grabCd <= 0 && d < 2.4 && random.nextInt(3) == 0 && startGrab(p, 1, Anims.M_GRAB, Anims.P_CHAINSAW)) {
            Voice.MS_ACELERA.say(this, true);
            grabCd = 300;
            return;
        }
        if (random.nextInt(3) == 0) startMove(new Move(Anims.M_OVERHEAD, 25, 45, 3.0, 22F, 60F), t);
        else startMove(new Move(Anims.M_SWING, 19, 39, 3.3, 16F, 130F), t);
        Voice.MS_ACELERA.say(this, false);
        attackCd = 25 + random.nextInt(25);
    }

    @Override
    protected void onHitTarget(Move m, LivingEntity e) {
        if (level() instanceof ServerLevel lv) {
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), e.getX(), e.getY() + 1.2, e.getZ(), 25, 0.2, 0.3, 0.2, 0.2);
        }
    }

    @Override
    protected double grabDistance() { return 1.06; }

    private int decap = -1;

    @Override
    protected void failGrab(Player p) {
        // DECAPITADO: la cabeza cae, el cuerpo se arrodilla y cae... y luego muere
        decap = 0;
        stop(Anims.M_GRAB);
        play(Anims.M_TAUNT);
        Voice.MS_CORTE.say(this, true);
        if (p instanceof ServerPlayer sp) {
            yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch pp = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(sp, yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
            if (pp != null) {
                pp.stopPlaying(Anims.P_CHAINSAW);
                pp.playAnimationSynchronized(Anims.P_DECAP, 0.05F);
            }
        }
    }

    private void tickDecap() {
        Entity e = getFirstPassenger();
        if (!(e instanceof Player p)) { decap = -1; return; }
        decap++;
        getNavigation().stop();
        float k = Math.min(1F, decap / 30F);
        if (level() instanceof ServerLevel lv && decap % 2 == 0 && decap < 34) {
            Vec3 neck = p.position().add(0, 1.5 - 0.9 * k, 0);
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), neck.x, neck.y, neck.z, 14, 0.08, 0.05, 0.08, 0.25);
            lv.sendParticles(ParticleTypes.DAMAGE_INDICATOR, neck.x, neck.y, neck.z, 2, 0.1, 0.1, 0.1, 0.1);
        }
        if (decap == 12) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 60));
        if (decap >= 42) {
            if (p instanceof ServerPlayer sp2) InfNet.CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp2), new InfNet.Hold(false, 1));
            releasing = true;
            p.hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Infectados.DECAPITATED), this), Float.MAX_VALUE);
            releasing = false;
            if (p.isAlive()) endGrab(p, false);
            decap = -1;
            attackCd = 60;
            Voice.MS_RISA.say(this, true);
        }
    }

    @Override
    protected void onForceRelease() {
        decap = -1;
        stop(Anims.M_GRAB);
    }

    @Override
    protected void endGrab(Player p, boolean escaped) {
        stop(Anims.M_GRAB);
        super.endGrab(p, escaped);
        if (escaped) Voice.MS_DOLOR.say(this, false);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (grabCd > 0) grabCd--;
        if (decap >= 0) tickDecap();
        else if (isGrabbing()) tickGrab();
        LivingEntity t = getTarget();
        if (t != null && lastTarget == null) {
            Voice.MS_RUGIDO.say(this, true);
            if (!busy()) startMove(new Move(Anims.M_TAUNT, 999, 36, 0, 0, 0), t);
        }
        lastTarget = t;
        if (--voiceCd <= 0) {
            voiceCd = 60 + random.nextInt(80);
            (t != null ? Voice.pick(random, Voice.MS_RUGIDO, Voice.MS_ACELERA, Voice.MS_RISA) : Voice.MS_MOTOR).say(this, false);
        }
        bar.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bar.removePlayer(p);
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) Voice.MS_MUERTE.say(this, true);
        super.die(src);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
}
