package com.starlevi.starframes.block;

import com.starlevi.starframes.StarFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Una pantalla: que canal muestra, de que tamaño (en bloques), corrida cuanto, a cuantos bloques se
 * escucha y si brilla sola (como una pantalla de verdad) o toma la luz del lugar.
 */
public class ScreenBlockEntity extends BlockEntity {
    /** Las pantallas cargadas en el cliente (para el sonido: cada una suena desde donde esta). */
    public static final Set<ScreenBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    private String channel = "";
    private float width = 2f, height = 1.125f, offsetX, offsetY;
    private int range = 24;
    private boolean bright = true;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(StarFrames.SCREEN_BE, pos, state);
    }

    public String channel() {
        return this.channel;
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    public float offsetX() {
        return this.offsetX;
    }

    public float offsetY() {
        return this.offsetY;
    }

    public int range() {
        return this.range;
    }

    public boolean bright() {
        return this.bright;
    }

    /** Cambia un valor (desde el menu) y lo manda a los clientes. */
    public void set(String field, String value) {
        try {
            switch (field) {
                case "canal" -> this.channel = value;
                case "ancho" -> this.width = clamp(Float.parseFloat(value), 0.25f, 32f);
                case "alto" -> this.height = clamp(Float.parseFloat(value), 0.25f, 32f);
                case "corrida_x" -> this.offsetX = clamp(Float.parseFloat(value), -32f, 32f);
                case "corrida_y" -> this.offsetY = clamp(Float.parseFloat(value), -32f, 32f);
                case "alcance" -> this.range = (int) clamp(Float.parseFloat(value), 4f, 128f);
                case "brillo" -> this.bright = Boolean.parseBoolean(value);
                default -> {
                    return;
                }
            }
        } catch (NumberFormatException e) {
            return;
        }
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public void setChannel(String id) {
        this.channel = id;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.channel = in.getStringOr("canal", "");
        this.width = in.getFloatOr("ancho", 2f);
        this.height = in.getFloatOr("alto", 1.125f);
        this.offsetX = in.getFloatOr("corrida_x", 0f);
        this.offsetY = in.getFloatOr("corrida_y", 0f);
        this.range = in.getIntOr("alcance", 24);
        this.bright = in.getBooleanOr("brillo", true);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("canal", this.channel);
        out.putFloat("ancho", this.width);
        out.putFloat("alto", this.height);
        out.putFloat("corrida_x", this.offsetX);
        out.putFloat("corrida_y", this.offsetY);
        out.putInt("alcance", this.range);
        out.putBoolean("brillo", this.bright);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null && this.level.isClientSide()) {
            CLIENT.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT.remove(this);
    }
}
