package com.leon.saintsdragons.common.block;

import com.leon.saintsdragons.server.entity.base.DragonGender;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;


public abstract class AbstractDragonEggBlockEntity extends BlockEntity {
    private static final double LEGACY_NORMAL_HATCH_TICKS = 18000.0D;

    private double hatchProgress;
    @Nullable
    private UUID ownerUUID;
    @Nullable
    private UUID hatchAdvancementOwnerUUID;
    @Nullable
    private DragonGender babyGender;
    private boolean pausedHatchingParticlesShown;

    protected AbstractDragonEggBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public double getHatchProgress() {
        return hatchProgress;
    }

    public void setHatchProgress(double hatchProgress) {
        this.hatchProgress = Math.max(0.0D, Math.min(1.0D, hatchProgress));
        this.setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        this.setChanged();
    }

    @Nullable
    public UUID getHatchAdvancementOwnerUUID() {
        return this.hatchAdvancementOwnerUUID != null ? this.hatchAdvancementOwnerUUID : this.ownerUUID;
    }

    public void setHatchAdvancementOwnerUUID(@Nullable UUID hatchAdvancementOwnerUUID) {
        this.hatchAdvancementOwnerUUID = hatchAdvancementOwnerUUID;
        this.setChanged();
    }

    @Nullable
    public DragonGender getBabyGender() {
        return this.babyGender;
    }

    public void setBabyGender(@Nullable DragonGender gender) {
        this.babyGender = gender;
        this.setChanged();
    }

    public boolean hasShownPausedHatchingParticles() {
        return this.pausedHatchingParticlesShown;
    }

    public void setPausedHatchingParticlesShown(boolean shown) {
        if (this.pausedHatchingParticlesShown != shown) {
            this.pausedHatchingParticlesShown = shown;
            this.setChanged();
        }
    }

    protected void sdSave(CompoundTag tag) {
        tag.putDouble("HatchProgress", this.hatchProgress);
        if (this.ownerUUID != null) {
            com.leon.saintsdragons.util.compat.Compat.putUUID(tag, "OwnerUUID", this.ownerUUID);
        }
        if (this.hatchAdvancementOwnerUUID != null) {
            com.leon.saintsdragons.util.compat.Compat.putUUID(tag, "HatchAdvancementOwnerUUID", this.hatchAdvancementOwnerUUID);
        }
        if (this.babyGender != null) {
            tag.putByte("BabyGender", this.babyGender.getId());
        }
        tag.putBoolean("PausedHatchingParticlesShown", this.pausedHatchingParticlesShown);
    }

    protected void sdLoad(CompoundTag tag) {
        if (tag.contains("HatchProgress")) {
            this.hatchProgress = Math.max(0.0D, Math.min(1.0D, tag.getDoubleOr("HatchProgress", 0.0D)));
        } else {
            double legacyTicks = Math.max(0, tag.getIntOr("HatchProgressTicks", 0));
            this.hatchProgress = Math.max(0.0D, Math.min(1.0D, legacyTicks / LEGACY_NORMAL_HATCH_TICKS));
        }
        if (com.leon.saintsdragons.util.compat.Compat.hasUUID(tag, "OwnerUUID")) {
            this.ownerUUID = com.leon.saintsdragons.util.compat.Compat.getUUID(tag, "OwnerUUID");
        }
        if (com.leon.saintsdragons.util.compat.Compat.hasUUID(tag, "HatchAdvancementOwnerUUID")) {
            this.hatchAdvancementOwnerUUID = com.leon.saintsdragons.util.compat.Compat.getUUID(tag, "HatchAdvancementOwnerUUID");
        }
        if (tag.contains("BabyGender")) {
            this.babyGender = DragonGender.fromId(tag.getByteOr("BabyGender", (byte) 0));
        }
        this.pausedHatchingParticlesShown = tag.getBooleanOr("PausedHatchingParticlesShown", false);
    }

    // port 1.21.11: el juego guarda con ValueOutput/ValueInput; los datos del mod siguen en un CompoundTag
    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput saintsdragonsOut) {
        super.saveAdditional(saintsdragonsOut);
        CompoundTag saintsdragonsTag = new CompoundTag();
        sdSave(saintsdragonsTag);
        saintsdragonsOut.store("saintsdragons", CompoundTag.CODEC, saintsdragonsTag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput saintsdragonsIn) {
        super.loadAdditional(saintsdragonsIn);
        sdLoad(saintsdragonsIn.read("saintsdragons", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
