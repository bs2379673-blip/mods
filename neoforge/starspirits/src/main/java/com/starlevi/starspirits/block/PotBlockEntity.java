package com.starlevi.starspirits.block;

import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.server.Caravan;
import com.starlevi.starspirits.server.Pending;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Lo que hay en la maceta: que espiritu y de quien es. */
public class PotBlockEntity extends BlockEntity {
    public String spirit = "";
    public UUID owner;
    public String ownerName = "";

    public PotBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.POT_BE.get(), pos, state);
    }

    public boolean empty() {
        return this.spirit.isEmpty();
    }

    public void use(ServerPlayer p) {
        if (empty()) {
            SpiritData d = Caravan.data(p);
            if (d.entries.isEmpty()) {
                p.displayClientMessage(Component.literal("🌱 No tenés espíritus para plantar"), true);
                return;
            }
            SpiritData.Entry e = d.potPick == null ? null : d.find(d.potPick);
            if (e == null) {
                e = d.entries.get(d.entries.size() - 1);
            }
            d.potPick = null;
            d.remove(e.id());
            Caravan.poof(e.id());
            this.spirit = e.type();
            this.owner = p.getUUID();
            this.ownerName = p.getGameProfile().getName();
            sync();
            burst(SoundEvents.AZALEA_LEAVES_PLACE);
            p.displayClientMessage(Component.literal("🌱 " + Caravan.displayName(this.spirit) + " vive ahora en esta maceta"), true);
            return;
        }
        if (!p.getUUID().equals(this.owner) && !p.hasPermissions(2)) {
            p.displayClientMessage(Component.literal("🌱 Este es el espíritu de " + this.ownerName), true);
            return;
        }
        Caravan.give(p, this.spirit);
        p.displayClientMessage(Component.literal("🌱 " + Caravan.displayName(this.spirit) + " vuelve a seguirte"), true);
        clear();
        burst(SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    /** Se rompio la maceta: el espiritu vuelve a su dueno (ahora o cuando entre). */
    public void release() {
        if (empty() || !(this.level instanceof ServerLevel sl)) {
            return;
        }
        ServerPlayer p = this.owner == null ? null : sl.getServer().getPlayerList().getPlayer(this.owner);
        if (p != null) {
            Caravan.give(p, this.spirit);
            p.displayClientMessage(Component.literal("🌱 " + Caravan.displayName(this.spirit) + " volvió con vos"), true);
        } else if (this.owner != null) {
            Pending.add(sl.getServer(), this.owner, this.spirit);
        }
        this.spirit = "";
    }

    private void clear() {
        this.spirit = "";
        this.owner = null;
        this.ownerName = "";
        sync();
    }

    private void burst(net.minecraft.sounds.SoundEvent s) {
        if (this.level instanceof ServerLevel sl) {
            BlockPos b = this.worldPosition;
            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, b.getX() + 0.5, b.getY() + 0.8, b.getZ() + 0.5, 14, 0.25, 0.2, 0.25, 0.02);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 0.8, b.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0);
            sl.playSound(null, b, s, SoundSource.BLOCKS, 1f, 1.3f);
        }
    }

    private void sync() {
        setChanged();
        if (this.level != null) {
            BlockState st = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.saveAdditional(tag, prov);
        tag.putString("espiritu", this.spirit);
        if (this.owner != null) {
            tag.putUUID("dueno", this.owner);
        }
        tag.putString("dueno_nombre", this.ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.loadAdditional(tag, prov);
        this.spirit = tag.getString("espiritu");
        this.owner = tag.hasUUID("dueno") ? tag.getUUID("dueno") : null;
        this.ownerName = tag.getString("dueno_nombre");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider prov) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, prov);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
