package com.tunombre.dancers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;
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
    /** Tick de juego en que empezo la entrada / la salida (0 = ninguna). */
    public static final EntityDataAccessor<Long> APPEAR = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.LONG);
    public static final EntityDataAccessor<Long> VANISH = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.LONG);
    /** holograma: copia translucida (gigante) que solo se ve, sin chispas ni aura */
    public static final EntityDataAccessor<Boolean> HOLO = SynchedEntityData.defineId(DancerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int APPEAR_TICKS = 60, VANISH_TICKS = 50;
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.25f, 0.95f, 0.85f), 1.6f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.2f);
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
        this.entityData.define(APPEAR, 0L);
        this.entityData.define(VANISH, 0L);
        this.entityData.define(HOLO, false);
    }

    public boolean isHolo() { return this.entityData.get(HOLO); }
    public void setHolo(boolean b) { this.entityData.set(HOLO, b); }
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

    /** Entrada: las particulas convergen en una estrella y de ella nace la figura. */
    public long getAppearStart() { return this.entityData.get(APPEAR); }
    public long getVanishStart() { return this.entityData.get(VANISH); }
    public void appear() { this.entityData.set(VANISH, 0L); this.entityData.set(APPEAR, this.level().getGameTime()); }
    /** Salida: la figura se contrae en una estrella que estalla en particulas; despues la entidad desaparece. */
    public void vanish() { if (this.entityData.get(VANISH) == 0L) this.entityData.set(VANISH, this.level().getGameTime()); }

    /** Escala del efecto de entrada/salida en [0,1] (1 = normal, 0 = oculta). Solo cliente (usa partialTick). */
    public float fxScale(float pt) {
        long now = this.level().getGameTime();
        long v = this.entityData.get(VANISH);
        if (v > 0) {
            float u = (now - v + pt) / VANISH_TICKS;
            if (u >= 0.75f) return 0f;
            float k = 1f - u / 0.75f;
            return k * k * (3 - 2 * k) * (0.9f + 0.1f * k);
        }
        long a = this.entityData.get(APPEAR);
        if (a > 0) {
            float u = (now - a + pt) / APPEAR_TICKS;
            if (u < 0.4f) return 0f;
            if (u >= 1f) return 1f;
            float k = (u - 0.4f) / 0.6f, c1 = 1.4f, c3 = c1 + 1f, x = k - 1f;
            return Math.max(0f, 1f + c3 * x * x * x + c1 * x * x);   // easeOutBack: sale con un pequeno rebote
        }
        return 1f;
    }

    @Override
    public void tick() {
        super.tick();
        long now = this.level().getGameTime();
        long v = this.entityData.get(VANISH);
        if (!this.level().isClientSide) {
            if (v > 0 && now >= v + VANISH_TICKS) this.discard();
            return;
        }
        if (this.isHolo()) return;
        double s = this.getDancerScale(), cx = this.getX(), cy = this.getY() + 0.95 * s, cz = this.getZ();
        var rnd = this.getRandom();
        long a = this.entityData.get(APPEAR);
        if (v > 0) {
            long t = now - v;
            if (t < VANISH_TICKS * 0.75) {
                for (int i = 0; i < 5; i++) {   // chispas que se escapan mientras se contrae
                    double ang = rnd.nextDouble() * Math.PI * 2, r = (0.4 + rnd.nextDouble() * 0.5) * s;
                    this.level().addParticle(i % 2 == 0 ? TEAL : ParticleTypes.END_ROD, cx + Math.cos(ang) * r, cy + (rnd.nextDouble() - 0.5) * 1.6 * s, cz + Math.sin(ang) * r,
                            Math.cos(ang) * 0.05, 0.04, Math.sin(ang) * 0.05);
                }
            } else if (t == (long) (VANISH_TICKS * 0.75)) {
                burst(cx, cy, cz, s, 120);
                this.level().playLocalSound(cx, cy, cz, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.2f, 1.3f, false);
                this.level().playLocalSound(cx, cy, cz, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.2f, 1.0f, false);
            }
        } else if (a > 0) {
            long t = now - a;
            if (t < APPEAR_TICKS * 0.4) {   // particulas que convergen hacia el centro
                for (int i = 0; i < 6; i++) {
                    double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(2 * rnd.nextDouble() - 1), r = (1.8 + rnd.nextDouble() * 1.4) * s;
                    double dx = Math.sin(ph) * Math.cos(th) * r, dy = Math.cos(ph) * r, dz = Math.sin(ph) * Math.sin(th) * r;
                    double sp = 0.08;
                    this.level().addParticle(i % 2 == 0 ? TEAL : ParticleTypes.END_ROD, cx + dx, cy + dy, cz + dz, -dx * sp, -dy * sp, -dz * sp);
                }
                if (t == 0) this.level().playLocalSound(cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0f, 1.8f, false);
            } else if (t == (long) (APPEAR_TICKS * 0.4)) {
                burst(cx, cy, cz, s, 70);
                this.level().addParticle(ParticleTypes.FLASH, cx, cy, cz, 0, 0, 0);
                this.level().playLocalSound(cx, cy, cz, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.2f, 1.2f, false);
            } else if (t < APPEAR_TICKS) {   // destellos mientras la figura crece
                for (int i = 0; i < 3; i++)
                    this.level().addParticle(i == 0 ? WHITE : TEAL, cx + (rnd.nextDouble() - 0.5) * 1.2 * s, cy + (rnd.nextDouble() - 0.5) * 1.8 * s, cz + (rnd.nextDouble() - 0.5) * 1.2 * s, 0, 0.03, 0);
            }
        }
    }

    private void burst(double x, double y, double z, double s, int n) {
        var rnd = this.getRandom();
        for (int i = 0; i < n; i++) {
            double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(2 * rnd.nextDouble() - 1), sp = (0.15 + rnd.nextDouble() * 0.35) * Math.max(0.6, s);
            double vx = Math.sin(ph) * Math.cos(th) * sp, vy = Math.cos(ph) * sp, vz = Math.sin(ph) * Math.sin(th) * sp;
            this.level().addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : i % 3 == 1 ? TEAL : ParticleTypes.ELECTRIC_SPARK, x, y, z, vx, vy, vz);
        }
        this.level().addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
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
        double h = Math.max(1.5 + 8.0 * this.getTravel(), 5.0) * s;
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
        tag.putBoolean("DancerHolo", this.isHolo());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DancerModel")) this.setModelName(tag.getString("DancerModel"));
        if (tag.contains("DancerScale")) this.setDancerScale(tag.getFloat("DancerScale"));
        if (tag.getBoolean("DancerHolo")) this.setHolo(true);
        if (tag.contains("DancerTravel")) this.setTravel(tag.getFloat("DancerTravel"));
    }
}
