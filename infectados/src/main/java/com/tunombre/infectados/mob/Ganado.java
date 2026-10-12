package com.tunombre.infectados.mob;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Aldeano poseido por la plaga (Resident Evil 4). Variantes 1-3 armadas (horca, hoz, hacha); la 4 va sin arma y es la que
 * te AGARRA (quick time event) mientras los demas te pegan. Gritan sus frases al verte, al perseguirte y si se te ponen
 * detras. Disparos a la cabeza: revienta, y a veces sale la PLAGA (guillotina con mucho alcance alrededor del cuello).
 */
public class Ganado extends InfMob {
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HEADLESS = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PLAGA_ATTACK = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.INT);

    private static final Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MOTIONS = Map.of(
            LivingMotions.IDLE, Anims.G_IDLE, LivingMotions.WALK, Anims.G_WALK, LivingMotions.CHASE, Anims.G_RUN, LivingMotions.SNEAK, Anims.G_HEADLESS);

    private int headHits;
    private int shoutCd = 60;
    private int behindCd;
    private LivingEntity lastTarget;
    private int plagaTick = -1;

    public Ganado(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, (byte) 1);
        entityData.define(HEADLESS, false);
        entityData.define(PLAGA_ATTACK, -1000);
    }

    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int v) { entityData.set(VARIANT, (byte) Math.max(1, Math.min(4, v))); }
    public boolean headless() { return entityData.get(HEADLESS); }
    /** tickCount en el que empezo el tajo de la plaga (para animarla en el cliente) */
    public int plagaAttackStart() { return entityData.get(PLAGA_ATTACK); }
    public boolean armed() { return variant() != 4; }

    @Override
    public Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions() { return MOTIONS; }

    @Override
    public LivingMotion forcedMotion() {
        if (!headless()) return null;
        return LivingMotions.SNEAK;      // sin cabeza siempre se tambalea
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this, 0.85, 1.15));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Ganado.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor lv, DifficultyInstance diff, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData r = super.finalizeSpawn(lv, diff, reason, data, tag);
        int v = random.nextInt(10) < 3 ? 4 : 1 + random.nextInt(3);
        setVariant(v);
        return r;
    }

    // ------------------------------------------------------------------ ataques ----
    private static final Move CHOP = new Move(null, 16, 30, 2.7, 7F, 80F);

    @Override
    protected double attackRange() { return headless() ? 3.0 : 2.4; }

    @Override
    protected void chooseAttack(LivingEntity t, double d) {
        if (headless()) {
            startPlaga();
            return;
        }
        if (!armed() && t instanceof Player p && random.nextInt(3) != 0 && startGrab(p, 0, Anims.G_GRAB, Anims.P_GRABBED)) {
            Voice.pick(random, Voice.LO_TENGO, Voice.AGARRADLO).say(this, true);
            alertOthers(p, Voice.AGARRADLO);
            return;
        }
        if (!armed()) {
            startMove(new Move(Anims.G_SWIPE, 13, 27, 2.2, 4F, 90F), t);
        } else if (random.nextBoolean()) {
            startMove(new Move(Anims.G_CHOP, 16, 30, 2.8, 7F, 70F), t);
        } else {
            startMove(new Move(Anims.G_SWIPE, 13, 27, 2.7, 6F, 120F), t);
        }
        if (random.nextInt(4) == 0) Voice.pick(random, Voice.MUERETE, Voice.PICADILLO, Voice.TE_VOY_A_MATAR, Voice.CERDO).say(this, false);
        attackCd = 20 + random.nextInt(20);
    }

    @Override
    protected void failGrab(Player p) {
        // no se suelta a tiempo: mordisco y lo tira
        p.hurt(damageSources().mobAttack(this), 7F);
        stop(Anims.G_GRAB);
        endGrab(p, false);
    }

    @Override
    protected void endGrab(Player p, boolean escaped) {
        stop(Anims.G_GRAB);
        super.endGrab(p, escaped);
    }

    // ------------------------------------------------------------------ plaga ----
    private void startPlaga() {
        plagaTick = 0;
        entityData.set(PLAGA_ATTACK, tickCount);
        play(Anims.G_PLAGA);
        getNavigation().stop();
        Voice.PLAGA_TAJO.say(this, true);
        attackCd = 50 + random.nextInt(30);
    }

    private void tickPlaga() {
        if (plagaTick < 0) return;
        plagaTick++;
        getNavigation().stop();
        // el tajo da la vuelta completa entre los ticks 15 y 25: todo lo que este a 3.3 bloques del cuello
        if (plagaTick >= 15 && plagaTick <= 25 && plagaTick % 3 == 0) {
            Vec3 neck = position().add(0, 1.5, 0);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.4, 1.2, 3.4), x -> x != this && !(x instanceof InfMob))) {
                if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                if (e.position().add(0, e.getBbHeight() * 0.6, 0).distanceTo(neck) < 3.4 && e.invulnerableTime <= 10) {
                    e.hurt(damageSources().mobAttack(this), 13F);
                    Vec3 away = e.position().subtract(position()).normalize();
                    e.knockback(0.8, -away.x, -away.z);
                }
            }
        }
        if (plagaTick > 30) plagaTick = -1;
    }

    // ------------------------------------------------------------------ golpes recibidos: la cabeza ----
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return super.hurt(src, amount);
        if (isGrabbing() && src.getEntity() == getFirstPassenger()) amount *= 0.5F;
        boolean head = false;
        Entity direct = src.getDirectEntity();
        if (!headless() && direct != null && direct != src.getEntity() && !(direct instanceof LivingEntity)) {
            // proyectil / bala: ¿a la altura de la cabeza?
            head = direct.getY() + direct.getBbHeight() / 2 >= getY() + 1.45;
        }
        if (head) {
            headHits++;
            amount *= 1.6F;
        }
        boolean lethal = getHealth() - amount <= 0;
        if (!headless() && head && (headHits >= 2 || lethal)) {
            boolean r = super.hurt(src, Math.min(amount, Math.max(0, getHealth() - 1)));
            popHead(src);
            return r;
        }
        if (src.getEntity() instanceof Player && random.nextInt(3) == 0) Voice.DOLOR.say(this, false);
        return super.hurt(src, amount);
    }

    private void popHead(DamageSource src) {
        ServerLevel lv = (ServerLevel) level();
        Vec3 h = position().add(0, 1.75, 0);
        lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), h.x, h.y, h.z, 60, 0.25, 0.25, 0.25, 0.25);
        lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), h.x, h.y, h.z, 30, 0.2, 0.2, 0.2, 0.2);
        Voice.CABEZA.say(this, true);
        if (move != null) {
            stop(move.anim());
            move = null;
        }
        Entity e = getFirstPassenger();
        if (e instanceof Player p) endGrab(p, true);
        if (random.nextFloat() < Infectados.cfgPlagaChance()) {
            entityData.set(HEADLESS, true);
            setHealth(Math.max(getHealth(), 22F));
            attackCd = 30;
            Voice.PLAGA.say(this, true);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2D);
        } else {
            kill();
        }
    }

    // ------------------------------------------------------------------ frases ----
    private void alertOthers(LivingEntity t, Voice v) {
        boolean said = false;
        for (Ganado g : level().getEntitiesOfClass(Ganado.class, getBoundingBox().inflate(18), x -> x != this && x.getTarget() == null)) {
            g.setTarget(t);
            if (!said && random.nextBoolean()) said = v.say(g, false);
        }
    }

    private void voices() {
        LivingEntity t = getTarget();
        if (t != null && lastTarget == null && !headless()) {
            Voice.pick(random, Voice.AHI_ESTA, Voice.AHI_ESTA, Voice.UN_FORASTERO, Voice.ALLI_ESTA).say(this, true);
            if (move == null && !isGrabbing()) {
                getNavigation().stop();
                startMove(new Move(Anims.G_POINT, 999, 26, 0, 0, 0), t);
            }
            alertOthers(t, Voice.pick(random, Voice.COGEDLO, Voice.AGARRADLO));
            shoutCd = 80 + random.nextInt(80);
        }
        lastTarget = t;
        if (headless()) return;
        if (t == null) {
            if (--shoutCd <= 0) {
                shoutCd = 200 + random.nextInt(400);
                Voice.MURMULLO.say(this, false);
            }
            return;
        }
        if (behindCd > 0) behindCd--;
        // ¡detras de ti, imbecil!: cuando el jugador le da la espalda estando cerca
        if (behindCd == 0 && t instanceof Player p && distanceToSqr(p) < 25) {
            Vec3 look = p.getViewVector(1F).multiply(1, 0, 1).normalize();
            Vec3 toMe = position().subtract(p.position()).multiply(1, 0, 1).normalize();
            if (look.dot(toMe) < -0.4 && Voice.DETRAS_DE_TI.say(this, false)) behindCd = 400;
        }
        if (--shoutCd <= 0) {
            shoutCd = 120 + random.nextInt(160);
            double d = distanceTo(t);
            if (d > 14) Voice.pick(random, Voice.SE_ESCAPA, Voice.COGEDLO).say(this, false);
            else Voice.pick(random, Voice.MATALO, Voice.PICADILLO, Voice.MORIR_ES_VIVIR, Voice.TE_VOY_A_MATAR, Voice.COGEDLO, Voice.CERDO).say(this, false);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (isGrabbing()) tickGrab();
        tickPlaga();
        voices();
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide && !headless()) Voice.MUERTE.say(this, true);
        super.die(src);
    }

    // ------------------------------------------------------------------ varios ----
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.PLAYER_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected float getSoundVolume() { return 0.6F; }
    @Override public boolean removeWhenFarAway(double d) { return !isGrabbing() && super.removeWhenFarAway(d); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Variant", (byte) variant());
        tag.putBoolean("Headless", headless());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) setVariant(tag.getByte("Variant"));
        entityData.set(HEADLESS, tag.getBoolean("Headless"));
    }
}
