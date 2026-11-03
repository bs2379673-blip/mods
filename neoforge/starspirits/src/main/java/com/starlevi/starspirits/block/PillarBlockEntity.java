package com.starlevi.starspirits.block;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.server.Caravan;
import com.starlevi.starspirits.server.SpiritLibrary;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** El ritual del pilar: el espiritu atrapado sube por la runa, brilla y sale vinculado a su dueno. */
public class PillarBlockEntity extends BlockEntity {
    /** Espiritu que se esta vinculando ("" si no hay ritual). */
    public String ritual = "";
    public UUID owner;
    public long start;
    public int duration = 90;

    public PillarBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PILLAR_BE.get(), pos, state);
    }

    public boolean ritualActive() {
        return !this.ritual.isEmpty();
    }

    public boolean startRitual(ServerPlayer p, String type) {
        if (ritualActive()) {
            p.displayClientMessage(Component.literal("🗿 El pilar ya está vinculando otro espíritu…"), true);
            return false;
        }
        this.ritual = type;
        this.owner = p.getUUID();
        this.start = this.level.getGameTime();
        this.duration = Config.ritualTicks;
        sync();
        this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 1.3f);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PillarBlockEntity be) {
        // arma el cuerpo invisible (los pilares del mundo aparecen solo con la base)
        if (level.getGameTime() % 40 == 0) {
            for (int i = 1; i < PillarBlock.HEIGHT; i++) {
                BlockPos b = pos.above(i);
                if (level.getBlockState(b).canBeReplaced()) {
                    level.setBlock(b, ModRegistry.PILLAR_BODY.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (!be.ritualActive()) {
            return;
        }
        long t = level.getGameTime() - be.start;
        ServerLevel sl = (ServerLevel) level;
        if (t % 6 == 0) {
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.8f + t / (float) be.duration);
        }
        if (t >= be.duration) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(be.owner);
            String type = be.ritual;
            be.ritual = "";
            be.sync();
            sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.12);
            sl.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5, 30, 1.2, 2, 1.2, 0.1);
            sl.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.2f);
            if (p != null) {
                Caravan.give(p, type);
                SpiritDef d = SpiritLibrary.get(type);
                p.sendSystemMessage(Component.literal("🗿 ¡" + Caravan.displayName(type) + " ahora es tuyo! ")
                        .withStyle(d == null ? ChatFormatting.GREEN : d.rarity.chat)
                        .append(Component.literal("Te va a seguir a todos lados.").withStyle(ChatFormatting.GRAY)));
            } else {
                // el dueno se fue: el espiritu queda guardado para cuando vuelva
                com.starlevi.starspirits.server.Pending.add(sl.getServer(), be.owner, type);
            }
        }
    }

    public void sync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putString("ritual", this.ritual);
        if (this.owner != null) {
            tag.putUUID("dueno", this.owner);
        }
        tag.putLong("inicio", this.start);
        tag.putInt("duracion", this.duration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        this.ritual = tag.getString("ritual");
        this.owner = tag.hasUUID("dueno") ? tag.getUUID("dueno") : null;
        this.start = tag.getLong("inicio");
        this.duration = tag.contains("duracion") ? tag.getInt("duracion") : 90;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
