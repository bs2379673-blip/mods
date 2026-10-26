package com.annis.npc.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * NPC con cuerpo de jugador. El servidor solo guarda datos (skin, emote, quieto);
 * el cliente se encarga de dibujar y animar.
 */
public class AnnisNpcEntity extends PathAwareEntity {
    private static final TrackedData<String> SKIN =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SLIM =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> EMOTE =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> EMOTE_SERIAL =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> STATIONARY =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Ticks que el NPC se queda quieto despues de lanzar un emote (solo servidor). */
    private int holdTicks = 0;

    public AnnisNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createNpcAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, "");
        builder.add(SLIM, false);
        builder.add(EMOTE, "");
        builder.add(EMOTE_SERIAL, 0);
        builder.add(STATIONARY, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(2, new NpcWanderGoal(this, 0.6));
        this.goalSelector.add(3, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && this.holdTicks > 0) {
            this.holdTicks--;
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // ---- datos ----

    public String getSkin() {
        return this.dataTracker.get(SKIN);
    }

    public void setSkin(String skin) {
        this.dataTracker.set(SKIN, skin == null ? "" : skin);
    }

    public boolean isSlim() {
        return this.dataTracker.get(SLIM);
    }

    public void setSlim(boolean slim) {
        this.dataTracker.set(SLIM, slim);
    }

    public String getEmote() {
        return this.dataTracker.get(EMOTE);
    }

    public int getEmoteSerial() {
        return this.dataTracker.get(EMOTE_SERIAL);
    }

    public boolean isStationary() {
        return this.dataTracker.get(STATIONARY);
    }

    public void setStationary(boolean value) {
        this.dataTracker.set(STATIONARY, value);
    }

    public boolean isBusy() {
        return isStationary() || this.holdTicks > 0;
    }

    /** Lanza un emote en los clientes. El NPC se queda quieto holdTicks ticks. */
    public void playEmote(String name, int holdTicks) {
        this.dataTracker.set(EMOTE, name == null ? "" : name);
        this.dataTracker.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = Math.max(0, holdTicks);
        this.getNavigation().stop();
    }

    public void stopEmote() {
        this.dataTracker.set(EMOTE, "");
        this.dataTracker.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = 0;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Skin", getSkin());
        nbt.putBoolean("Slim", isSlim());
        nbt.putBoolean("Stationary", isStationary());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setSkin(nbt.getString("Skin"));
        setSlim(nbt.getBoolean("Slim"));
        setStationary(nbt.getBoolean("Stationary"));
    }

    /** Caminar al azar, pero solo si el NPC no esta quieto ni haciendo un emote. */
    private static class NpcWanderGoal extends WanderAroundFarGoal {
        private final AnnisNpcEntity npc;

        NpcWanderGoal(AnnisNpcEntity npc, double speed) {
            super(npc, speed);
            this.npc = npc;
        }

        @Override
        public boolean canStart() {
            return !npc.isBusy() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !npc.isBusy() && super.shouldContinue();
        }
    }
}
