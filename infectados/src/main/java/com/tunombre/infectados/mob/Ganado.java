package com.tunombre.infectados.mob;

import net.minecraft.util.Mth;

import com.tunombre.infectados.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
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
import java.util.List;
import java.util.Map;

/**
 * Aldeano poseido por la plaga (Resident Evil 4). Variantes 1-3 armadas (horca, hoz, hacha); la 4 va sin arma y es la que
 * te AGARRA (quick time event) mientras los demas te pegan. Gritan sus frases al verte, al perseguirte y si se te ponen
 * detras. Disparos a la cabeza: revienta, y a veces sale la PLAGA (guillotina con mucho alcance alrededor del cuello).
 */
public class Ganado extends InfMob {
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> OUTFIT = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HEADLESS = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PLAGA_ATTACK = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> IMPALE_START = SynchedEntityData.defineId(Ganado.class, EntityDataSerializers.INT);

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
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, (byte) 1);
        entityData.define(OUTFIT, (byte) 0);
        entityData.define(HEADLESS, false);
        entityData.define(PLAGA_ATTACK, -1000);
        entityData.define(IMPALE_START, -1000);
    }

    public int variant() { return entityData.get(VARIANT); }
    /** atuendo (0-2): mismos modelos con ropa de otros colores para que no sean todos iguales */
    public int outfit() { return entityData.get(OUTFIT); }
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
        goalSelector.addGoal(2, new ChaseGoal(this, 1.0, 1.45));
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
        entityData.set(OUTFIT, (byte) random.nextInt(3));
        return r;
    }

    // ------------------------------------------------------------------ ataques ----
    private static final Move CHOP = new Move(null, 16, 30, 2.7, 7F, 80F);

    @Override
    protected double attackRange() { return headless() ? 3.0 : variant() == 1 ? 3.8 : 4.0; }

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
        if (variant() == 1 && t instanceof ServerPlayer sp && (d > 2.5 || random.nextBoolean()) && !Grabs.recentlyGrabbed(sp)) {
            startThrust(sp);
            return;
        }
        if (d > 4.3) return;
        if (!armed()) {
            startMove(new Move(Anims.G_SWIPE, 8, 16, 2.4, 4F, 100F), t);
        } else if (random.nextBoolean()) {
            startMove(new Move(Anims.G_CHOP, 9, 17, 2.9, 7F, 80F), t);
        } else {
            startMove(new Move(Anims.G_SWIPE, 8, 16, 2.9, 6F, 130F), t);
        }
        // envion: salta hacia el objetivo justo antes del golpe (mas lejos = mas impulso)
        lunge = Mth.clamp((d - 1.4) * 0.42, 0.18, 0.95);
        if (random.nextInt(4) == 0) Voice.pick(random, Voice.MUERETE, Voice.PICADILLO, Voice.TE_VOY_A_MATAR, Voice.CERDO).say(this, false);
        attackCd = 12 + random.nextInt(14);
    }

    @Override
    protected void failGrab(Player p) {
        // no se suelta a tiempo: mordisco y lo tira
        p.hurt(damageSources().mobAttack(this), 7F);
        stop(Anims.G_GRAB);
        endGrab(p, false);
    }

    @Override
    protected void onForceRelease() {
        stop(Anims.G_GRAB);
        entityData.set(IMPALE_START, -1000);
        closePrompt();
        dodgeState = 0;
    }

    @Override
    protected void endGrab(Player p, boolean escaped) {
        stop(Anims.G_GRAB);
        super.endGrab(p, escaped);
    }


    // ------------------------------------------------------------------ estocada con la horca (esquiva con una tecla) ----
    private ServerPlayer dodgePlayer;
    private int dodgeKey;
    private byte dodgeState;        // 0 nada, 1 esperando tecla, 2 esquivo, 3 fallo

    private void startThrust(ServerPlayer p) {
        dodgeKey = random.nextInt(2);
        // sin el mod de quick time en el jugador no hay aviso de esquiva: el ataque se vuelve un golpe normal
        List<String> ph = List.of(dodgeKey == 0 ? QteDefs.HORCA_IZQ : QteDefs.HORCA_DER);
        if (!Qte.available(p, ph)) {
            startMove(new Move(Anims.G_SWIPE, 8, 16, 2.9, 6F, 130F), p);
            attackCd = 30;
            return;
        }
        startMove(new Move(Anims.G_THRUST, 15, 26, 3.7, 0F, 50F), p);
        dodgePlayer = p;
        dodgeState = 1;
        Qte.start(p, ph, (won, reason) -> onDodge(p, won));
        Voice.pick(random, Voice.MUERETE, Voice.PICADILLO, Voice.TE_VOY_A_MATAR).say(this, false);
        attackCd = 40 + random.nextInt(30);
    }

    /** resultado del quick time de esquiva: si llega antes de la estocada, se esquiva */
    private void onDodge(ServerPlayer p, boolean won) {
        if (dodgeState != 1 || dodgePlayer != p) return;
        if (won) {
            dodgeState = 2;
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 right = new Vec3(-f.z, 0, f.x);
            Vec3 side = dodgeKey == 1 ? right : right.scale(-1);
            p.setDeltaMovement(side.x * 0.95, 0.28, side.z * 0.95);
            p.hurtMarked = true;
        } else {
            dodgeState = 3;
        }
    }

    private void closePrompt() {
        if (dodgePlayer != null) Qte.cancel(dodgePlayer);
    }

    @Override
    protected void onMoveHit(Move m) {
        if (m.anim() != Anims.G_THRUST) {
            super.onMoveHit(m);
            return;
        }
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(m.reach()), x -> x != this && x.isAlive() && !(x instanceof InfMob))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e == dodgePlayer && dodgeState == 2) continue;          // lo esquivo
            Vec3 to = e.position().subtract(position());
            double dd = to.horizontalDistance();
            if (dd > m.reach() + e.getBbWidth() / 2) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (to.x * f.x + to.z * f.z) / Math.max(dd, 1e-4)))));
            if (ang > m.arc() / 2) continue;
            if (e == dodgePlayer && e instanceof Player pl && startImpale(pl)) continue;
            // otros que estaban delante: pinchazo y empujon
            e.hurt(damageSources().mobAttack(this), Infectados.cfgImpale() * 0.5F);
            e.setDeltaMovement(f.x * 1.2, 0.4, f.z * 1.2);
            e.hurtMarked = true;
        }
        if (dodgeState == 1) closePrompt();
        dodgeState = 0;
        dodgePlayer = null;
    }

    private void tickDodge() {
        if (dodgeState == 1 && (move == null || move.anim() != Anims.G_THRUST)) {   // interrumpido
            closePrompt();
            dodgeState = 0;
            dodgePlayer = null;
        }
    }


    // ------------------------------------------------------------------ EMPALADO: clavado, levantado y arrojado ----
    /** punta de la horca respecto al aldeano (adelante, altura) en el tick k del empalamiento: igual que la animacion */
    private static final float[][] TIP = {{0, 1.7F, 0.95F}, {6, 1.6F, 1.1F}, {18, 0.45F, 2.75F}, {26, 0.35F, 2.85F}, {30, -0.6F, 2.6F}};
    public static final int THROW_TICK = 26;

    public static float[] impaleTip(float k) {
        if (k <= TIP[0][0]) return new float[]{TIP[0][1], TIP[0][2]};
        for (int i = 0; i < TIP.length - 1; i++) {
            float[] a = TIP[i], b = TIP[i + 1];
            if (k <= b[0]) {
                float x = (k - a[0]) / (b[0] - a[0]);
                x = x * x * (3 - 2 * x);
                return new float[]{a[1] + (b[1] - a[1]) * x, a[2] + (b[2] - a[2]) * x};
            }
        }
        float[] l = TIP[TIP.length - 1];
        return new float[]{l[1], l[2]};
    }

    public int impaleTick() {
        int s = entityData.get(IMPALE_START);
        return s < -500 ? -1 : tickCount - s;
    }

    private boolean startImpale(Player p) {
        if (p.isPassenger() || !p.isAlive()) return false;   // ya lo tiene otro: solo pinchazo normal
        move = null;
        if (!p.startRiding(this, true)) return false;
        entityData.set(IMPALE_START, tickCount);
        closePrompt();
        dodgeState = 0;
        p.hurt(damageSources().mobAttack(this), Infectados.cfgImpale());
        play(Anims.G_IMPALE);
        if (p instanceof ServerPlayer sp) {
            yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch pp = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(sp, yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
            if (pp != null) pp.playAnimationSynchronized(Anims.P_IMPALED, 0.05F);
        }
        Voice.pick(random, Voice.MUERETE, Voice.PICADILLO, Voice.CERDO).say(this, true);
        if (level() instanceof ServerLevel lv) {
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.2, 0.3, 0.2, 0.3);
        }
        attackCd = 60;
        return true;
    }

    private void tickImpale() {
        int k = impaleTick();
        Entity e = getFirstPassenger();
        getNavigation().stop();
        if (!(e instanceof Player p)) {
            entityData.set(IMPALE_START, -1000);
            return;
        }
        if (level() instanceof ServerLevel lv && k % 3 == 0) {
            lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), p.getX(), p.getY() + 0.9, p.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
        }
        if (k >= THROW_TICK) {
            // lo arroja por encima de su cabeza hacia atras
            releasing = true;
            p.stopRiding();
            releasing = false;
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            p.setDeltaMovement(-f.x * 1.35, 0.6, -f.z * 1.35);
            p.hurtMarked = true;
            p.fallDistance = 0;
            Grabs.mark(p);
            entityData.set(IMPALE_START, -1000);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction cb) {
        int k = impaleTick();
        if (k < 0 || !hasPassenger(passenger)) {
            super.positionRider(passenger, cb);
            return;
        }
        float[] tip = impaleTip(Math.min(k, THROW_TICK));
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        double fw = tip[0] + 0.12, up = tip[1] - 0.95;
        cb.accept(passenger, getX() + f.x * fw, getY() + up, getZ() + f.z * fw);
        passenger.setYBodyRot(getYRot() + 180F);
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
        if (impaleTick() >= 0) tickImpale();
        else if (isGrabbing()) tickGrab();
        tickPlaga();
        tickDodge();
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
        tag.putByte("Outfit", (byte) outfit());
        tag.putBoolean("Headless", headless());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) setVariant(tag.getByte("Variant"));
        entityData.set(OUTFIT, (byte) Math.max(0, Math.min(2, tag.getByte("Outfit"))));
        entityData.set(HEADLESS, tag.getBoolean("Headless"));
    }
}
