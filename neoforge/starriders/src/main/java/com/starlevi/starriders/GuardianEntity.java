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
    private int sinkTicks = -1;

    public GuardianEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 120.0).add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.SAFE_FALL_DISTANCE, 32.0)
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
        return new Vec3(0, 2.05, 0.1);
    }

    @Override
    protected void scared(Player p) {
        p.displayClientMessage(Component.literal("🌳 El guardián se asustó y se hunde en la tierra…"), true);
        this.wildState = 3;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------ montado

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
        if (jumping(p) && onGround() && this.leapCooldown == 0) {
            // salto largo hacia adelante
            Vec3 look = Vec3.directionFromRotation(0, yaw);
            double push = p.isSprinting() ? 0.9 : 0.55;
            setDeltaMovement(getDeltaMovement().add(look.x * push * Math.max(0.3, fwd), 0.72, look.z * push * Math.max(0.3, fwd)));
            this.hasImpulse = true;
            this.leapCooldown = 14;
            if (!this.level().isClientSide) {
                this.level().playSound(null, blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL, 1.2f, 0.7f);
            }
        }
        setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (p.isSprinting() ? 1.75f : 1f));
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
