package com.starlevi.starriders;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * La Serpiente Gigante: una cobra enorme que se desliza por el piso. El jinete va sentado en el lomo,
 * detras del cuello levantado. No salta; con sprint se desliza mucho mas rapido. Salvaje: deambula
 * y se va hundiendose en la tierra, como el guardian.
 */
public class SerpentEntity extends GuardianEntity {
    public SerpentEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 120.0).add(Attributes.MOVEMENT_SPEED, 0.13)
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.SAFE_FALL_DISTANCE, 32.0);
    }

    @Override
    public String mountType() {
        return "serpiente";
    }

    @Override
    public String icon() {
        return "🐍";
    }

    @Override
    public String label() {
        return "serpiente";
    }

    /**
     * Montada: flota sobre el piso; con salto levita unos segundos enroscandose hacia arriba y despues
     * planea hacia donde mira el jinete, bajando despacio.
     */
    @Override
    protected void ridden(Player p, Vec3 in) {
        float yaw = p.getYRot();
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(p.getXRot() * 0.5f);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        float fwd = p.zza, side = p.xxa;
        if (this.glideCooldown > 0) {
            this.glideCooldown--;
        }
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        if (this.levitate > 0) {
            this.levitate--;
            setNoGravity(true);
            Vec3 want = new Vec3(look.x * 0.12 * Math.max(0, fwd), 0.26, look.z * 0.12 * Math.max(0, fwd));
            Vec3 v = getDeltaMovement().lerp(want, 0.12);
            setDeltaMovement(v);
            move(net.minecraft.world.entity.MoverType.SELF, v);
            if (this.levitate == 0) {
                this.gliding = true;
            }
            resetFallDistance();
            calculateEntityAnimation(false);
            return;
        }
        if (this.gliding && !onGround()) {
            setNoGravity(true);
            double speed = fwd > 0 ? (p.isSprinting() ? 0.85 : 0.6) : 0.3;
            Vec3 want = look.scale(speed).add(0, fwd > 0 ? -0.06 : -0.12, 0);
            Vec3 v = getDeltaMovement().lerp(want, 0.07);
            setDeltaMovement(v);
            move(net.minecraft.world.entity.MoverType.SELF, v);
            resetFallDistance();
            calculateEntityAnimation(false);
            return;
        }
        if (this.gliding) {
            // aterrizo
            this.gliding = false;
            this.glideCooldown = 30;
        }
        setNoGravity(false);
        if (jumping(p) && onGround() && this.glideCooldown == 0) {
            this.levitate = 60;
            setDeltaMovement(getDeltaMovement().add(0, 0.3, 0));
            if (!this.level().isClientSide) {
                roar();
            }
            return;
        }
        setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (p.isSprinting() ? sprintBoost() : 1f));
        travelPlain(new Vec3(side * 0.4, in.y, fwd < 0 ? fwd * 0.4 : fwd));
        resetFallDistance();
    }

    /** Flota sobre el agua: la superficie le sirve de piso (como el strider en la lava). */
    @Override
    public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState fluid) {
        return fluid.is(net.minecraft.tags.FluidTags.WATER);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // si igual queda dentro del agua (corriente, o aparecio abajo) sube despacio hasta la superficie
        if (isInWater() && !isNoGravity()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.9, Math.min(0.22, Math.max(v.y, 0) + 0.06), v.z * 0.9);
            resetFallDistance();
        }
        // sin jinete en el aire: baja planeando suave
        if (!this.level().isClientSide && getControllingPassenger() == null && (this.gliding || this.levitate > 0)) {
            this.levitate = 0;
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.95, Math.max(v.y - 0.02, -0.15), v.z * 0.95);
            if (onGround()) {
                this.gliding = false;
                setNoGravity(false);
            }
        }
    }

    @Override
    protected boolean canLeap() {
        return false;
    }

    @Override
    protected float sprintBoost() {
        return 1.9f;
    }

    @Override
    protected void roarSound() {
        this.level().playSound(null, blockPosition(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 2.0f, 0.35f);
        this.level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    /** Cuantos pueden ir sentados sobre el enroscado (el dueno detras del cuello, el resto alrededor). */
    public static final int SEATS = 6;
    /** Donde se sienta cada uno (bloques, ejes de la serpiente: x costado, y alto, z adelante), flotando incluido. */
    public static final float[][] SEAT_POS = {{0.3f, 2.7f, -1.41f}, {0.82f, 2.65f, -0.58f}, {-0.95f, 2.0f, -1.92f},
            {-2.27f, 1.88f, 0.22f}, {-1.52f, 1.74f, 1.49f}, {-2.05f, 1.2f, -1.15f}};
    private int levitate, glideCooldown;
    private boolean gliding;

    /** Asiento de un pasajero: 0 el dueno, despues los demas en el orden en que subieron. */
    public int seatOf(Entity e) {
        if (e.getUUID().equals(ownerId())) {
            return 0;
        }
        int n = 1;
        for (Entity p : getPassengers()) {
            if (p == e) {
                return n;
            }
            if (!p.getUUID().equals(ownerId())) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions dims, float scale) {
        float[] p = SEAT_POS[Math.min(seatOf(e), SEATS - 1)];
        return new Vec3(p[0], p[1], p[2]);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        // solo la maneja el dueno, aunque haya subido despues que los demas
        for (Entity p : getPassengers()) {
            if (p instanceof Player pl && pl.getUUID().equals(ownerId())) {
                return pl;
            }
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        if (getPassengers().size() >= SEATS) {
            return false;
        }
        // el lugar del dueno siempre queda libre para el
        return e.getUUID().equals(ownerId()) || getPassengers().size() < SEATS - 1
                || getPassengers().stream().anyMatch(p -> p.getUUID().equals(ownerId()));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isWild() || player.isSecondaryUseActive() || hasPassenger(player)) {
            return super.mobInteract(player, hand);
        }
        // cualquiera puede subirse al lomo; solo el dueno la maneja
        if (!this.level().isClientSide) {
            if (canAddPassenger(player)) {
                player.startRiding(this);
                if (!player.getUUID().equals(ownerId())) {
                    player.displayClientMessage(Component.literal("🐍 Te subiste al lomo de la serpiente (la maneja su dueño)"), true);
                }
            } else {
                player.displayClientMessage(Component.literal("🐍 No hay más lugar en el lomo"), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void scared(Player p) {
        p.displayClientMessage(Component.literal("🐍 La serpiente sisea y se mete bajo tierra…"), true);
        this.wildState = 3;
        this.stateTicks = 0;
    }
}
