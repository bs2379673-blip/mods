package com.tunombre.obscurearcade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Maquina recreativa: una cancion de CubOsu, coste opcional, premio por rango y top 5. */
public class ArcadeEntity extends BlockEntity {
    public String song = "";
    public String costItem = "obscurearcade:arcade_token";
    public int costCount = 0;
    public String rewardCmd = "";
    public String rewardRank = "";
    public final List<CompoundTag> top = new ArrayList<>();

    public ArcadeEntity(BlockPos pos, BlockState state) { super(ObscureArcade.ARCADE_BE.get(), pos, state); }

    public void record(String name, int score, float acc, String rank) {
        top.removeIf(t -> t.getString("n").equals(name) && t.getInt("s") <= score);
        if (top.stream().anyMatch(t -> t.getString("n").equals(name))) return;
        CompoundTag t = new CompoundTag();
        t.putString("n", name);
        t.putInt("s", score);
        t.putFloat("a", acc);
        t.putString("r", rank);
        top.add(t);
        top.sort((a, b) -> Integer.compare(b.getInt("s"), a.getInt("s")));
        while (top.size() > 5) top.remove(top.size() - 1);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("song", song);
        tag.putString("costItem", costItem);
        tag.putInt("costCount", costCount);
        tag.putString("rewardCmd", rewardCmd);
        tag.putString("rewardRank", rewardRank);
        ListTag l = new ListTag();
        l.addAll(top);
        tag.put("top", l);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        song = tag.getString("song");
        costItem = tag.contains("costItem") ? tag.getString("costItem") : "obscurearcade:arcade_token";
        costCount = tag.getInt("costCount");
        rewardCmd = tag.getString("rewardCmd");
        rewardRank = tag.getString("rewardRank");
        top.clear();
        for (Tag t : tag.getList("top", Tag.TAG_COMPOUND)) top.add((CompoundTag) t);
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
