package com.tunombre.dancers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Bailarina: no se mueve ni tiene IA; su aspecto y su animacion van sincronizados con los clientes. */
public class DancerEntity extends PathfinderMob {
    public static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> ANIM = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.LONG);
    public static final EntityDataAccessor<Boolean> LOOP = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> TRAVEL = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.FLOAT);

    /** Solo cliente: estado de los resortes (pelo). */
    public Object clientSprings;
    public long clientSpringsT;

    public DancerEntity(EntityType<? extends DancerEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODEL, "miku");
        this.entityData.define(ANIM, "");
        this.entityData.define(START, 0L);
        this.entityData.define(LOOP, false);
        this.entityData.define(SCALE, 1.0f);
        this.entityData.define(TRAVEL, 0.0f);
    }

    public String getModelName() { return this.entityData.get(MODEL); }
    public void setModelName(String s) { this.entityData.set(MODEL, s); }
    public float getDancerScale() { return this.entityData.get(SCALE); }
    public void setDancerScale(float s) { this.entityData.set(SCALE, Math.max(0.05f, Math.min(s, 64f))); this.refreshDimensions(); }
    public float getTravel() { return this.entityData.get(TRAVEL); }
    public void setTravel(float t) { this.entityData.set(TRAVEL, Math.max(0f, Math.min(t, 1f))); }
    public String getAnim() { return this.entityData.get(ANIM); }
    public long getStart() { return this.entityData.get(START); }
    public boolean isLooping() { return this.entityData.get(LOOP); }

    /** Empieza (o reinicia) una animacion; todos los que reciban la orden en el mismo tick quedan sincronizados. */
    public void play(String animId, boolean loop) {
        this.entityData.set(ANIM, animId);
        this.entityData.set(LOOP, loop);
        this.entityData.set(START, this.level().getGameTime());
    }

    public void stopAnim() { this.entityData.set(ANIM, ""); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (SCALE.equals(key)) this.refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(this.getDancerScale());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float s = this.getDancerScale();
        double h = (1.5 + 8.0 * this.getTravel()) * s;
        return this.getBoundingBox().inflate(h, 0.6 * s, h);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("DancerModel", this.getModelName());
        tag.putFloat("DancerScale", this.getDancerScale());
        tag.putFloat("DancerTravel", this.getTravel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DancerModel")) this.setModelName(tag.getString("DancerModel"));
        if (tag.contains("DancerScale")) this.setDancerScale(tag.getFloat("DancerScale"));
        if (tag.contains("DancerTravel")) this.setTravel(tag.getFloat("DancerTravel"));
    }
}
