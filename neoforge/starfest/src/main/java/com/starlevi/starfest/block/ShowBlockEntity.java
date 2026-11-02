package com.starlevi.starfest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Bloque de show (foco, reflector, piso, cabina, lanzador). Lo que se anima vive solo en el cliente. */
public class ShowBlockEntity extends BlockEntity {
    /** Cliente: giro y caida actuales del cabezal (radianes), suavizados. */
    public float pan;
    public float tilt;
    public boolean init;
    public long lastFrame;
    /** Cliente: ultima vez que salio un cohete (ms). */
    public long lastLaunch;
    public int lastBeat = -1;

    public ShowBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
