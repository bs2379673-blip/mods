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
    /** Salvaje: anda suelto por el mundo, se escapa de los jugadores y se puede atrapar. */
    private static final EntityDataAccessor<Boolean> WILD = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.BOOLEAN);
    /** Salvaje cansado (se queda quieto jadeando): es el momento de atraparlo. */
    private static final EntityDataAccessor<Boolean> TIRED = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.BOOLEAN);
    private int wildAge, fleeTicks, restTicks;

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
        b.define(WILD, false);
        b.define(TIRED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WildFleeGoal());
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.55) {
            @Override
            public boolean canUse() {
                return isWild() && !isTired() && super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, Player.class, 10f) {
            @Override
            public boolean canUse() {
                return isWild() && super.canUse();
            }
        });
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

    public boolean isWild() {
        return this.entityData.get(WILD);
    }

    public void setWild(boolean w) {
        this.entityData.set(WILD, w);
    }

    public boolean isTired() {
        return this.entityData.get(TIRED);
    }

    /** Huye de los jugadores un rato y despues se cansa unos segundos (ahi se lo puede atrapar). */
    private final class WildFleeGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private Player from;

        WildFleeGoal() {
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private Player threat() {
            // agachado y despacito se puede acercar mucho mas sin que se de cuenta
            return level().getNearestPlayer(SpiritEntity.this.getX(), SpiritEntity.this.getY(), SpiritEntity.this.getZ(), 9,
                    e -> e instanceof Player p && !p.isSpectator() && (!p.isShiftKeyDown() || p.distanceTo(SpiritEntity.this) < 3.5f));
        }

        @Override
        public boolean canUse() {
            if (!isWild() || isTired()) {
                return false;
            }
            this.from = threat();
            return this.from != null;
        }

        @Override
        public boolean canContinueToUse() {
            return isWild() && !isTired() && this.from != null && this.from.isAlive() && this.from.distanceTo(SpiritEntity.this) < 14;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone() || SpiritEntity.this.tickCount % 10 == 0) {
                net.minecraft.world.phys.Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(SpiritEntity.this, 12, 5, this.from.position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.45);
                }
            }
            if (++SpiritEntity.this.fleeTicks > 90) {
                // se cansa: se queda quieto un ratito
                SpiritEntity.this.fleeTicks = 0;
                SpiritEntity.this.restTicks = 55;
                SpiritEntity.this.entityData.set(TIRED, true);
                getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            this.from = null;
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
        if (hand == InteractionHand.MAIN_HAND && isWild()) {
            if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
                com.starlevi.starspirits.server.Wild.capture(sp, this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
        if (!this.level().isClientSide && isWild()) {
            this.wildAge++;
            if (isTired() && --this.restTicks <= 0) {
                this.entityData.set(TIRED, false);
            }
            if (!isTired() && this.fleeTicks > 0 && this.tickCount % 4 == 0 && this.level().getNearestPlayer(this, 12) == null) {
                this.fleeTicks = Math.max(0, this.fleeTicks - 4);
            }
            // se va si nadie lo encuentra
            if (this.wildAge > Config.wildMinutes * 1200 || (this.tickCount % 100 == 0 && this.level().getNearestPlayer(this, 96) == null)) {
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                }
                discard();
            }
        }
        if (this.level().isClientSide && clientTick != null) {
            clientTick.accept(this);
        }
    }
}
