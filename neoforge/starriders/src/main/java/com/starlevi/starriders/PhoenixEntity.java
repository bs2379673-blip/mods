package com.starlevi.starriders;

import com.starlevi.starriders.net.Net;
import com.starlevi.starriders.server.Mounts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

/**
 * El Fenix. Montado: camina en el piso; con salto despega y vuela hacia donde mirás (adelante acelera,
 * sprint mas rapido, salto sube, sin avanzar planea bajando). Salvaje: se posa, da vueltas en el
 * cielo y se va al rato; se doma dandole manzanas doradas.
 */
public class PhoenixEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WILD = SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ROAR = SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.INT);
    private static Field jumpingField;

    /** Servidor: la montura del jugador a la que corresponde. */
    public UUID mountId;
    /** Lado del jinete: estado de vuelo predicho (se avisa al servidor). */
    private boolean localFlying;
    private int takeoffTicks;
    private int idleTicks;
    // salvaje
    protected Vec3 home;
    protected int wildState; // 0 posado, 1 dando vueltas, 2 bajando, 3 yendose
    protected int stateTicks, lifeTicks, trust;
    private float circleAngle;

    public PhoenixEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.SAFE_FALL_DISTANCE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER, Optional.empty());
        b.define(FLYING, false);
        b.define(WILD, false);
        b.define(ROAR, -1000);
    }

    // ------------------------------------------------------------------ estado

    public UUID ownerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwner(UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    public boolean isWild() {
        return this.entityData.get(WILD);
    }

    public void setWild(boolean w) {
        this.entityData.set(WILD, w);
        if (w) {
            this.home = position();
        }
    }

    /** Vuela ahora (para el jinete local, lo que predice su cliente). */
    public boolean isFlyingNow() {
        if (this.level().isClientSide && getControllingPassenger() instanceof Player p && p.isLocalPlayer()) {
            return this.localFlying;
        }
        return this.entityData.get(FLYING);
    }

    public void setFlying(boolean f) {
        this.entityData.set(FLYING, f);
        this.setNoGravity(f);
    }

    private void setFlyingLocal(boolean f) {
        if (this.localFlying != f) {
            this.localFlying = f;
            PacketDistributor.sendToServer(new Net.Flight(getId(), f));
        }
    }

    public void roar() {
        this.entityData.set(ROAR, this.tickCount);
        roarSound();
    }

    protected void roarSound() {
        this.level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 0.55f, 1.7f);
        this.level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.8f, 0.8f);
    }

    /** Tipo de montura (como se guarda en el silbato). */
    public String mountType() {
        return "fenix";
    }

    /** Emoji y nombre corto para los mensajes. */
    public String icon() {
        return "🔥";
    }

    public String label() {
        return "fénix";
    }

    /** Lo que pasa cuando un jugador le pega a una salvaje. */
    protected void scared(Player p) {
        p.displayClientMessage(Component.literal("🔥 El fénix se asustó y se va volando…"), true);
        this.wildState = 3;
        this.stateTicks = 0;
    }

    /** 0..1 mientras grita, -1 si no. */
    public float roarProgress(float partial) {
        float t = (this.tickCount + partial - this.entityData.get(ROAR)) / 40f;
        return t >= 0 && t <= 1 ? t : -1;
    }

    // ------------------------------------------------------------------ no se guarda, no muere

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (isWild() && source.getEntity() instanceof Player p && !this.level().isClientSide) {
            scared(p);
        }
        return false;
    }

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ------------------------------------------------------------------ jinete

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isWild()) {
            if (held.is(Items.GOLDEN_APPLE) || held.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                if (!this.level().isClientSide && this.wildState != 3) {
                    feed((ServerPlayer) player, held);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide) {
                player.displayClientMessage(Component.literal(icon() + " Parece hambriento… ¿le darías algo dorado?"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!player.getUUID().equals(ownerId())) {
            if (!this.level().isClientSide) {
                player.displayClientMessage(Component.literal(icon() + " Este " + label() + " tiene dueño"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!isVehicle() && !player.isSecondaryUseActive()) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private void feed(ServerPlayer p, ItemStack held) {
        if (!p.getAbilities().instabuild) {
            held.shrink(1);
        }
        ServerLevel sl = (ServerLevel) this.level();
        this.lifeTicks = Math.max(0, this.lifeTicks - 600); // se queda un rato mas
        boolean ok = this.random.nextFloat() < (held.is(Items.ENCHANTED_GOLDEN_APPLE) ? 1f : 0.7f);
        this.level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1f, 0.8f);
        if (ok) {
            this.trust += held.is(Items.ENCHANTED_GOLDEN_APPLE) ? 2 : 1;
            sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.2, getZ(), 4, 0.5, 0.3, 0.5, 0);
        } else {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.2, getZ(), 8, 0.4, 0.3, 0.4, 0.01);
        }
        getLookControl().setLookAt(p, 40, 40);
        if (this.trust >= Config.tameFeedings) {
            Mounts.tame(this, p);
        } else {
            p.displayClientMessage(Component.literal(icon() + (ok ? " El " + label() + " confía un poco más en vos" : " El " + label() + " duda…")), true);
        }
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof Player p && p.getUUID().equals(ownerId()) ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions dims, float scale) {
        return new Vec3(0, 1.55, -0.1);
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        if (!this.level().isClientSide && e instanceof LivingEntity le && !onGround()) {
            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
        }
    }

    protected static boolean jumping(LivingEntity e) {
        try {
            if (jumpingField == null) {
                jumpingField = LivingEntity.class.getDeclaredField("jumping");
                jumpingField.setAccessible(true);
            }
            return jumpingField.getBoolean(e);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ movimiento

    @Override
    public void travel(Vec3 in) {
        if (!isAlive()) {
            return;
        }
        if (getControllingPassenger() instanceof Player p) {
            ridden(p, in);
            return;
        }
        if (isFlyingNow()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.96));
            calculateEntityAnimation(true);
            return;
        }
        super.travel(in);
    }

    protected void ridden(Player p, Vec3 in) {
        float yaw = p.getYRot();
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(p.getXRot() * 0.5f);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        boolean jump = jumping(p);
        float fwd = p.zza, side = p.xxa;
        boolean flying = isFlyingNow();
        if (!flying) {
            if (jump && (onGround() || isInWater())) {
                setFlyingLocal(true);
                this.takeoffTicks = 18;
                setDeltaMovement(getDeltaMovement().add(0, 0.85, 0));
            } else {
                setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (p.isSprinting() ? 1.7f : 1f));
                super.travel(new Vec3(side * 0.5, in.y, fwd < 0 ? fwd * 0.4 : fwd));
                return;
            }
        }
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), yaw);
        double target = fwd > 0 ? (p.isSprinting() ? Settings.flyBoost : Settings.flySpeed) : fwd < 0 ? 0.12 : 0.4;
        Vec3 desired = look.scale(target);
        if (jump) {
            desired = desired.add(0, 0.5, 0);
        } else if (fwd <= 0) {
            desired = desired.add(0, -0.14, 0);
        }
        Vec3 v = getDeltaMovement().lerp(desired, this.takeoffTicks > 0 ? 0.04 : 0.075);
        if (this.takeoffTicks > 0) {
            this.takeoffTicks--;
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        resetFallDistance();
        if (this.horizontalCollision) {
            setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
        }
        if (onGround() && this.takeoffTicks == 0 && v.y <= 0.05 && !jump) {
            setFlyingLocal(false);
        }
        calculateEntityAnimation(true);
    }

    /** Movimiento normal de mob (sin la logica de vuelo del fenix). */
    protected void travelPlain(Vec3 v) {
        super.travel(v);
    }

    /** Velocidades de vuelo (el servidor las manda al entrar; el cliente las usa para predecir). */
    public static final class Settings {
        public static double flySpeed = 0.9, flyBoost = 1.45;

        private Settings() {
        }
    }

    // ------------------------------------------------------------------ cada tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            if (clientTick != null) {
                clientTick.accept(this);
            }
            return;
        }
        if (isWild()) {
            wildTick();
            return;
        }
        if (getControllingPassenger() == null) {
            // sin jinete: si estaba volando, baja planeando; al rato vuelve al silbato
            if (this.entityData.get(FLYING)) {
                Vec3 v = getDeltaMovement();
                setDeltaMovement(v.x * 0.95, Math.max(v.y - 0.02, -0.25), v.z * 0.95);
                if (onGround()) {
                    setFlying(false);
                }
            }
            Player owner = ownerId() == null ? null : this.level().getPlayerByUUID(ownerId());
            if (owner != null && distanceToSqr(owner) < 24 * 24) {
                getLookControl().setLookAt(owner, 20, 20);
            }
            if (++this.idleTicks > Config.idleMinutes * 1200) {
                Mounts.recall(this, true);
            }
        } else {
            this.idleTicks = 0;
        }
    }

    /** El servidor recibio del jinete que despego o aterrizo. */
    public void clientSaysFlying(ServerPlayer from, boolean f) {
        if (from == getControllingPassenger()) {
            setFlying(f);
            if (f) {
                roar();
            }
        }
    }

    /** Particulas y sonidos del lado del cliente (los pone el cliente). */
    public static java.util.function.Consumer<PhoenixEntity> clientTick;

    // ------------------------------------------------------------------ salvaje

    protected void wildTick() {
        if (this.home == null) {
            this.home = position();
        }
        this.lifeTicks++;
        this.stateTicks++;
        if (this.lifeTicks > Config.wildMinutes * 1200 && this.wildState != 3) {
            this.wildState = 3;
            this.stateTicks = 0;
        }
        switch (this.wildState) {
            case 0 -> { // posado: mira a los jugadores cercanos
                if (this.entityData.get(FLYING)) {
                    setFlying(false);
                }
                Player near = this.level().getNearestPlayer(this, 16);
                if (near != null) {
                    getLookControl().setLookAt(near, 30, 30);
                }
                if (this.stateTicks > 500 + this.random.nextInt(400) && (near == null || distanceTo(near) > 10)) {
                    this.wildState = 1;
                    this.stateTicks = 0;
                    setFlying(true);
                    setDeltaMovement(0, 0.7, 0);
                    roar();
                }
            }
            case 1 -> { // da vueltas en el cielo sobre su lugar
                this.circleAngle += 0.025f;
                double gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) this.home.x, (int) this.home.z) + 14;
                Vec3 target = new Vec3(this.home.x + Mth.cos(this.circleAngle) * 14, gy, this.home.z + Mth.sin(this.circleAngle) * 14);
                steer(target, 0.55);
                if (this.stateTicks > 400 + this.random.nextInt(300)) {
                    this.wildState = 2;
                    this.stateTicks = 0;
                }
            }
            case 2 -> { // baja a posarse
                double gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) this.home.x, (int) this.home.z);
                steer(new Vec3(this.home.x, gy, this.home.z), 0.4);
                if (onGround() || this.stateTicks > 600) {
                    this.wildState = 0;
                    this.stateTicks = 0;
                    setFlying(false);
                }
            }
            default -> { // se va volando lejos y desaparece
                setFlying(true);
                Vec3 away = this.home == null ? new Vec3(1, 0, 0) : position().subtract(this.home).multiply(1, 0, 1);
                if (away.lengthSqr() < 1) {
                    away = new Vec3(1, 0, 0);
                }
                steer(position().add(away.normalize().scale(20)).add(0, 12, 0), 0.9);
                if (this.stateTicks > 220) {
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 40, 0.8, 0.8, 0.8, 0.05);
                    }
                    discard();
                }
            }
        }
    }

    protected void steer(Vec3 target, double speed) {
        Vec3 to = target.subtract(position());
        Vec3 want = to.lengthSqr() > 1 ? to.normalize().scale(speed) : Vec3.ZERO;
        Vec3 v = getDeltaMovement().lerp(want, 0.06);
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 0.001) {
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 6));
            this.yBodyRot = getYRot();
            this.yHeadRot = getYRot();
        }
    }
}
