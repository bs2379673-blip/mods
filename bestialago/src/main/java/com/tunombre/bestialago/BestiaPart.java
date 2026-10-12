package com.tunombre.bestialago;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** Trozo de hitbox de la Bestia (cabeza y cola). Los golpes pasan a la bestia (que es inmortal). */
public class BestiaPart extends PartEntity<Bestia> {
    private final String name;
    private final EntityDimensions size;

    public BestiaPart(Bestia parent, String name, float w, float h) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(w, h);
        this.refreshDimensions();
    }

    @Override protected void defineSynchedData() { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource src, float amount) { return !isInvulnerableTo(src) && getParent().hurtFromPart(this, src, amount); }
    @Override public boolean is(Entity e) { return this == e || getParent() == e; }
    @Override public EntityDimensions getDimensions(Pose pose) { return size; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public ItemStack getPickResult() { return getParent().getPickResult(); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { throw new UnsupportedOperationException(); }

    public String partName() { return name; }
}
