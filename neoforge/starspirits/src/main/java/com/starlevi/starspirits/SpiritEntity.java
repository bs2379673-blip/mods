package com.starlevi.starspirits;

import com.starlevi.starspirits.server.Caravan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Un espiritu en el mundo: corre atras de su dueno (en caravana). No se guarda en el mapa: el servidor
 * lo vuelve a crear desde los datos del jugador. No recibe dano ni empuja a nadie.
 */
public class SpiritEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tick (del entity) en que empezo un saltito de alegria. */
    private static final EntityDataAccessor<Integer> HAPPY = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.INT);

    /** Particulas y detalles del lado del cliente (lo pone el cliente). */
    public static java.util.function.Consumer<SpiritEntity> clientTick;

    /** Id de la entrada en los datos del dueno (solo servidor). */
    public UUID entryId;

    public SpiritEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(TYPE, "");
        b.define(OWNER, Optional.empty());
        b.define(ACTIVE, false);
        b.define(HAPPY, -100);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    public String spiritType() {
        return this.entityData.get(TYPE);
    }

    public void setSpiritType(String t) {
        this.entityData.set(TYPE, t);
    }

    public UUID ownerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwner(UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    public boolean isActiveSpirit() {
        return this.entityData.get(ACTIVE);
    }

    public void setActiveSpirit(boolean a) {
        if (a != isActiveSpirit()) {
            this.entityData.set(ACTIVE, a);
        }
    }

    public void hop() {
        this.entityData.set(HAPPY, this.tickCount);
    }

    /** 0..1 durante el saltito de alegria, -1 si no salta. */
    public float happyProgress(float partial) {
        float t = (this.tickCount + partial - this.entityData.get(HAPPY)) / 16f;
        return t >= 0 && t <= 1 ? t : -1;
    }

    // ------------------------------------------------------------------ no se guarda, no muere, no empuja

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
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
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
    public void push(Entity e) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** Para /summon starspirits:espiritu ~ ~ ~ {tipo:"champi"} (exhibiciones; no se guardan). */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("tipo")) {
            setSpiritType(tag.getString("tipo"));
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("tipo", spiritType());
    }

    // ------------------------------------------------------------------ interaccion

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getUUID().equals(ownerId())) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && this.entryId != null) {
            if (player.isShiftKeyDown()) {
                sp.getData(ModRegistry.DATA.get()).potPick = this.entryId;
                sp.displayClientMessage(Component.literal("🌱 " + Caravan.displayName(spiritType())
                        + " va a la próxima maceta que toques"), true);
            } else {
                Caravan.setActive(sp, this.entryId);
            }
            this.hop();
            this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8f, 1.6f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && clientTick != null) {
            clientTick.accept(this);
        }
    }
}
