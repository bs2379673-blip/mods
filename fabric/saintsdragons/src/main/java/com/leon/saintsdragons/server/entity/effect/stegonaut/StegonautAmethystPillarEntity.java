package com.leon.saintsdragons.server.entity.effect.stegonaut;

import com.leon.saintsdragons.common.registry.ModEntities;
import com.leon.saintsdragons.server.entity.dragons.stegonaut.Stegonaut;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StegonautAmethystPillarEntity extends Entity implements GeoEntity {
    private static final RawAnimation EMERGE =
            RawAnimation.begin().thenPlay("animation.amethyst_pillar_emerge");
    private static final RawAnimation SUBSIDE =
            RawAnimation.begin().thenPlay("animation.amethyst_pillar_subside");
    private static final EntityDimensions BASE_DIMENSIONS = EntityDimensions.scalable(2.0F, 4.0F);
    private static final EntityDataAccessor<Boolean> DATA_SUBSIDING =
            SynchedEntityData.defineId(StegonautAmethystPillarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(StegonautAmethystPillarEntity.class, EntityDataSerializers.FLOAT);

    private static final int DEFAULT_WARMUP_TICKS = 4;
    private static final int DEFAULT_LIFETIME_TICKS = 26;
    private static final int SUBSIDE_TICKS = 10;
    private static final double DAMAGE_RADIUS = 4.5D;
    private static final double DAMAGE_HEIGHT = 7.5D;
    private static final double DEFAULT_KNOCKBACK_STRENGTH = 0.9D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Stegonaut owner;
    private UUID ownerUUID;
    private float damage = 10.0F;
    private double knockbackStrength = DEFAULT_KNOCKBACK_STRENGTH;
    private int warmupTicks = DEFAULT_WARMUP_TICKS;
    private int lifetimeTicks = DEFAULT_LIFETIME_TICKS;
    private int livedTicks;
    private int subsideTicks;
    private final Set<UUID> hitEntities = new HashSet<>();

    public StegonautAmethystPillarEntity(EntityType<? extends StegonautAmethystPillarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public StegonautAmethystPillarEntity(Level level, Vec3 pos, Stegonaut owner, float yaw, float damage, double knockbackStrength) {
        this(ModEntities.STEGONAUT_AMETHYST_PILLAR.get(), level);
        setPos(pos);
        this.owner = owner;
        this.ownerUUID = owner != null ? owner.getUUID() : null;
        this.damage = damage;
        this.knockbackStrength = knockbackStrength;
        initializeRotation(yaw);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_SUBSIDING, false);
        builder.define(DATA_SCALE, 1.0F);
    }

    public boolean isSubsiding() {
        return this.entityData.get(DATA_SUBSIDING);
    }

    public float getVisualScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setVisualScale(float scale) {
        this.entityData.set(DATA_SCALE, Math.max(0.5F, scale));
        refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        livedTicks++;

        if (level().isClientSide()) {
            spawnClientEffects();
        }

        if (isSubsiding()) {
            subsideTicks++;
            if (!level().isClientSide() && subsideTicks >= SUBSIDE_TICKS) {
                discard();
            }
            return;
        }

        if (!level().isClientSide()) {
            resolveOwner();
            if (livedTicks >= warmupTicks) {
                applyImpact();
            }
            if (livedTicks >= lifetimeTicks) {
                beginSubside();
            }
        }
    }

    private void beginSubside() {
        if (!isSubsiding()) {
            subsideTicks = 0;
            this.entityData.set(DATA_SUBSIDING, true);
        }
    }

    private void resolveOwner() {
        if (owner == null && ownerUUID != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(ownerUUID);
            if (entity instanceof Stegonaut stegonaut) {
                owner = stegonaut;
            }
        }
    }

    private void applyImpact() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }

        AABB area = new AABB(
                getX() - DAMAGE_RADIUS, getY() - 0.5D, getZ() - DAMAGE_RADIUS,
                getX() + DAMAGE_RADIUS, getY() + DAMAGE_HEIGHT, getZ() + DAMAGE_RADIUS
        );
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, area, target -> {
            if (!target.isAlive() || !target.attackable() || hitEntities.contains(target.getUUID())) {
                return false;
            }
            return owner == null || (target != owner && !owner.isAlly(target));
        });

        DamageSource source = owner != null ? damageSources().mobAttack(owner) : damageSources().generic();
        for (LivingEntity target : targets) {
            hitEntities.add(target.getUUID());
            target.hurtOrSimulate(source, damage);
            Vec3 knockDir = target.position().subtract(position());
            knockDir = new Vec3(knockDir.x, 0.0D, knockDir.z);
            if (knockDir.lengthSqr() < 1.0E-4D) {
                knockDir = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 push = knockDir.normalize().scale(knockbackStrength);
            target.push(push.x, 0.32D, push.z);
            target.hurtMarked = true;
            if (target instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(target));
            }
        }
    }

    private void spawnClientEffects() {
        if (level().random.nextFloat() < 0.35F) {
            level().addParticle(
                    ParticleTypes.END_ROD,
                    getX() + (level().random.nextDouble() - 0.5D) * 0.65D,
                    getY() + level().random.nextDouble() * 3.0D,
                    getZ() + (level().random.nextDouble() - 0.5D) * 0.65D,
                    0.0D, 0.02D, 0.0D
            );
        }
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel saintsdragons$level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    
    protected void sdLoad(CompoundTag tag) {
        livedTicks = tag.getIntOr("Lived", 0);
        lifetimeTicks = tag.getIntOr("Lifetime", 0);
        warmupTicks = tag.getIntOr("Warmup", 0);
        damage = tag.getFloatOr("Damage", 0.0F);
        knockbackStrength = tag.contains("Knockback") ? tag.getDoubleOr("Knockback", 0.0D) : DEFAULT_KNOCKBACK_STRENGTH;
        if (com.leon.saintsdragons.util.compat.Compat.hasUUID(tag, "Owner")) {
            ownerUUID = com.leon.saintsdragons.util.compat.Compat.getUUID(tag, "Owner");
        }
        if (tag.contains("Yaw")) {
            initializeRotation(tag.getFloatOr("Yaw", 0.0F));
        }
        this.entityData.set(DATA_SUBSIDING, tag.getBooleanOr("Subsiding", false));
        subsideTicks = Math.max(0, tag.getIntOr("SubsideTicks", 0));
    }

    
    protected void sdSave(CompoundTag tag) {
        tag.putInt("Lived", livedTicks);
        tag.putInt("Lifetime", lifetimeTicks);
        tag.putInt("Warmup", warmupTicks);
        tag.putFloat("Damage", damage);
        tag.putDouble("Knockback", knockbackStrength);
        tag.putFloat("Yaw", getYRot());
        tag.putBoolean("Subsiding", isSubsiding());
        tag.putInt("SubsideTicks", subsideTicks);
        if (ownerUUID != null) {
            com.leon.saintsdragons.util.compat.Compat.putUUID(tag, "Owner", ownerUUID);
        }
    }

    @Override
    public void recreateFromPacket(@NotNull ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        initializeRotation(packet.getYRot());
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return BASE_DIMENSIONS.scale(getVisualScale());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 1, this::animationPredicate));
    }

    private <E extends GeoEntity> PlayState animationPredicate(AnimationTest<E> state) {
        state.controller().transitionLength(1);
        state.controller().setAnimation(isSubsiding() ? SUBSIDE : EMERGE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private void initializeRotation(float yaw) {
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        this.yRotO = yaw;
        this.xRotO = 0.0F;
    }

    // port 1.21.11: el juego guarda con ValueOutput/ValueInput; los datos del mod siguen en un CompoundTag
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput saintsdragonsOut) {
        CompoundTag saintsdragonsTag = new CompoundTag();
        sdSave(saintsdragonsTag);
        saintsdragonsOut.store("saintsdragons", CompoundTag.CODEC, saintsdragonsTag);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput saintsdragonsIn) {
        sdLoad(saintsdragonsIn.read("saintsdragons", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
