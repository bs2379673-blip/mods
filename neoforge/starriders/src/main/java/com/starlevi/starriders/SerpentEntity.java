package com.starlevi.starriders;

import net.minecraft.network.chat.Component;
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

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions dims, float scale) {
        return new Vec3(0, 1.05, 0);
    }

    @Override
    protected void scared(Player p) {
        p.displayClientMessage(Component.literal("🐍 La serpiente sisea y se mete bajo tierra…"), true);
        this.wildState = 3;
        this.stateTicks = 0;
    }
}
