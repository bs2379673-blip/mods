package com.tunombre.obscurearcade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Consola de duelo: canciones de cada lado, estilo de pelea, comando final y los dos puestos del ring. */
public class VersusEntity extends BlockEntity {
    public String song1 = "";
    public String song2 = "";
    public String style = "pelea";
    public String endCmd = "";
    public final boolean[] hasSpot = new boolean[2];
    public final double[] sx = new double[2], sy = new double[2], sz = new double[2];

    public VersusEntity(BlockPos pos, BlockState state) { super(ObscureArcade.VERSUS_BE.get(), pos, state); }

    /** puesto del luchador i: el que se fijo, o uno por defecto a los lados de la consola */
    public Vec3 spot(int i) {
        if (hasSpot[i]) return new Vec3(sx[i], sy[i], sz[i]);
        BlockPos p = getBlockPos();
        return new Vec3(p.getX() + 0.5 + (i == 0 ? -2.5 : 2.5), p.getY(), p.getZ() + 0.5 + 3.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("song1", song1);
        tag.putString("song2", song2);
        tag.putString("style", style);
        tag.putString("endCmd", endCmd);
        for (int i = 0; i < 2; i++) {
            tag.putBoolean("has" + i, hasSpot[i]);
            tag.putDouble("sx" + i, sx[i]);
            tag.putDouble("sy" + i, sy[i]);
            tag.putDouble("sz" + i, sz[i]);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        song1 = tag.getString("song1");
        song2 = tag.getString("song2");
        style = tag.contains("style") ? tag.getString("style") : "pelea";
        endCmd = tag.getString("endCmd");
        for (int i = 0; i < 2; i++) {
            hasSpot[i] = tag.getBoolean("has" + i);
            sx[i] = tag.getDouble("sx" + i);
            sy[i] = tag.getDouble("sy" + i);
            sz[i] = tag.getDouble("sz" + i);
        }
    }
}
