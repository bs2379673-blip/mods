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

    /** Cuantos pueden ir sentados en el lomo (el dueno adelante, el resto en fila detras). */
    public static final int SEATS = 8;
    /** Separacion entre asientos (bloques). */
    public static final float SPACING = 1.8f;

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
        return new Vec3(0, 1.05, -seatOf(e) * SPACING);
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
