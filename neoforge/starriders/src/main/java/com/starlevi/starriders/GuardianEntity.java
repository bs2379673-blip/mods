package com.starlevi.starriders;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * El Guardian del Bosque: una bestia de raices que va por tierra. Montado: camina, con sprint corre
 * y con salto pega un salto largo. Salvaje: deambula cerca de donde aparecio y, cuando se cansa o
 * lo asustan, se hunde en la tierra. Se doma con manzanas doradas, como el fenix.
 */
public class GuardianEntity extends PhoenixEntity {
    private int leapCooldown;
    /** Raices enganchadas: punto donde se agarraron y tick en que se lanzaron (-1 = no hay). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> ROOT_AT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GuardianEntity.class, net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ROOT_TICK =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GuardianEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** Lado del que maneja (prediccion): raices activas, punto y ticks. */
    private Vec3 grappleAt;
    private int grappleTicks = -1;
    /** Distancia maxima a la que se pueden lanzar las raices. */
    public static final double ROOT_RANGE = 24;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ROOT_AT, new org.joml.Vector3f());
        b.define(ROOT_TICK, -1);
    }

    /** Punto donde estan enganchadas las raices (para dibujarlas), o null. */
    public Vec3 rootAnchor() {
        if (this.grappleTicks >= 0 && this.grappleAt != null) {
            return this.grappleAt;
        }
        if (this.entityData.get(ROOT_TICK) < 0) {
            return null;
        }
        org.joml.Vector3f v = this.entityData.get(ROOT_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    /** Ticks desde que lanzo las raices (con decimales), o -1. */
    public float rootAge(float partial) {
        if (this.grappleTicks >= 0) {
            return this.grappleTicks + partial;
        }
        int t = this.entityData.get(ROOT_TICK);
        return t < 0 ? -1 : this.tickCount - t + partial;
    }

    private void startGrapple(Vec3 at) {
        this.grappleAt = at;
        this.grappleTicks = 0;
        if (!this.level().isClientSide) {
            this.entityData.set(ROOT_AT, new org.joml.Vector3f((float) at.x, (float) at.y, (float) at.z));
            this.entityData.set(ROOT_TICK, this.tickCount);
            this.level().playSound(null, blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f);
            this.level().playSound(null, BlockPos.containing(at), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.5f, 0.5f);
        }
    }

    private void endGrapple() {
        this.grappleTicks = -1;
        this.grappleAt = null;
        setNoGravity(false);
        if (!this.level().isClientSide) {
            this.entityData.set(ROOT_TICK, -1);
        }
    }

    /** Raices: 8 ticks se estiran y se clavan; despues tiran del cuerpo hasta arriba. */
    private void grappleTick() {
        int t = ++this.grappleTicks;
        Vec3 chest = position().add(0, 2.2, 0);
        Vec3 to = this.grappleAt.subtract(chest);
        double d = to.length();
        if (t < 8) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            return;
        }
        if (d < 2.4 || t > 80 || (t > 20 && getDeltaMovement().lengthSqr() < 0.004)) {
            // llego: se impulsa un poquito para subirse arriba
            setDeltaMovement(to.normalize().scale(0.35).add(0, 0.6, 0));
            this.hasImpulse = true;
            endGrapple();
            this.leapCooldown = 10;
            return;
        }
        setNoGravity(true);
        double speed = Math.min(1.1, 0.25 + (t - 8) * 0.05);
        Vec3 v = getDeltaMovement().lerp(to.normalize().scale(speed), 0.35);
        setDeltaMovement(v);
        move(net.minecraft.world.entity.MoverType.SELF, v);
        if (t % 5 == 0 && !this.level().isClientSide) {
            this.level().playSound(null, blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8f, 0.6f);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.grappleTicks >= 0 && getControllingPassenger() == null) {
            endGrapple();
        }
    }

    /** Con salto mirando hacia arriba (a un lugar alto): lanza raices en vez de saltar. */
    private boolean tryGrapple(Player p) {
        if (p.getXRot() > -12) {
            return false;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(ROOT_RANGE));
        net.minecraft.world.phys.BlockHitResult hit = this.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getLocation().y < getY() + 2.5) {
            return false;
        }
        startGrapple(hit.getLocation());
        return true;
    }
    private int sinkTicks = -1;

    public GuardianEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 120.0).add(Attributes.MOVEMENT_SPEED, 0.11)
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.STEP_HEIGHT, 2.6).add(Attributes.SAFE_FALL_DISTANCE, 32.0)
                .add(Attributes.JUMP_STRENGTH, 0.6);
    }

    @Override
    public String mountType() {
        return "guardian";
    }

    @Override
    public String icon() {
        return "🌳";
    }

    @Override
    public String label() {
        return "guardián";
    }

    @Override
    public boolean isFlyingNow() {
        return false;
    }

    @Override
    public void setFlying(boolean f) {
        super.setFlying(false);
    }

    @Override
    protected void roarSound() {
        this.level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 1.0f, 0.75f);
        this.level().playSound(null, blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0f, 0.6f);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions dims, float scale) {
        return new Vec3(0.2, 2.9, 0.8);
    }

    @Override
    protected void scared(Player p) {
        p.displayClientMessage(Component.literal("🌳 El guardián se asustó y se hunde en la tierra…"), true);
        this.wildState = 3;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------ montado

    /** Con salto pega un salto largo (la serpiente no). */
    protected boolean canLeap() {
        return true;
    }

    /** Cuanto mas rapido va con sprint. */
    protected float sprintBoost() {
        return 1.8f;
    }

    @Override
    protected void ridden(Player p, Vec3 in) {
        float yaw = p.getYRot();
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(p.getXRot() * 0.5f);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        float fwd = p.zza, side = p.xxa;
        if (this.leapCooldown > 0) {
            this.leapCooldown--;
        }
        if (this.grappleTicks >= 0 && this.grappleAt != null) {
            grappleTick();
            resetFallDistance();
            calculateEntityAnimation(false);
            return;
        }
        if (canLeap() && jumping(p) && this.leapCooldown == 0 && tryGrapple(p)) {
            return;
        }
        if (canLeap() && jumping(p) && onGround() && this.leapCooldown == 0) {
            // salto largo hacia adelante
            Vec3 look = Vec3.directionFromRotation(0, yaw);
            double push = p.isSprinting() ? 0.9 : 0.55;
            setDeltaMovement(getDeltaMovement().add(look.x * push * Math.max(0.3, fwd), 0.85, look.z * push * Math.max(0.3, fwd)));
            this.hasImpulse = true;
            this.leapCooldown = 14;
            if (!this.level().isClientSide) {
                this.level().playSound(null, blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL, 1.2f, 0.7f);
            }
        }
        setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (p.isSprinting() ? sprintBoost() : 1f));
        travelPlain(new Vec3(side * 0.4, in.y, fwd < 0 ? fwd * 0.4 : fwd));
        resetFallDistance();
    }

    // ------------------------------------------------------------------ salvaje

    @Override
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
        if (this.wildState == 3) {
            // se hunde en la tierra entre raices y polvo
            getNavigation().stop();
            if (this.sinkTicks < 0) {
                this.sinkTicks = 0;
                roar();
            }
            this.sinkTicks++;
            if (this.level() instanceof ServerLevel sl) {
                BlockState below = this.level().getBlockState(blockPosition().below());
                if (!below.isAir()) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.2, getZ(), 12, 0.9, 0.1, 0.9, 0.1);
                }
                if (this.sinkTicks > 50) {
                    sl.sendParticles(ParticleTypes.COMPOSTER, getX(), getY() + 1, getZ(), 30, 1, 1, 1, 0.05);
                    discard();
                }
            }
            return;
        }
        Player near = this.level().getNearestPlayer(this, 16);
        if (near != null && near.distanceTo(this) < 10) {
            getNavigation().stop();
            getLookControl().setLookAt(near, 30, 30);
            return;
        }
        // deambula tranquilo cerca de su lugar
        if (getNavigation().isDone() && this.random.nextInt(120) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 3 + this.random.nextDouble() * 9;
            BlockPos to = BlockPos.containing(this.home.x + Math.cos(a) * r, this.home.y, this.home.z + Math.sin(a) * r);
            getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.7);
        }
    }
}
