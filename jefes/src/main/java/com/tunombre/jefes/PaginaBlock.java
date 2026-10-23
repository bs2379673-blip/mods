package com.tunombre.jefes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Una pagina de Slenderman pegada a una pared. Clic derecho (o romperla en supervivencia) la junta. */
public class PaginaBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty NUM = IntegerProperty.create("num", 0, 7);
    private static final VoxelShape N = Block.box(3, 1, 15, 13, 15, 16), S = Block.box(3, 1, 0, 13, 15, 1), E = Block.box(0, 1, 3, 1, 15, 13), W = Block.box(15, 1, 3, 16, 15, 13);

    public PaginaBlock() {
        super(BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.WOOL).noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NUM, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, NUM); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (s.getValue(FACING)) { case NORTH -> N; case SOUTH -> S; case EAST -> E; default -> W; };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getClickedFace().getAxis().isHorizontal() ? c.getClickedFace() : c.getHorizontalDirection().getOpposite();
        int n = 0;
        if (c.getLevel() instanceof ServerLevel lv) { Slender.Game g = Slender.Game.get(lv.getServer()); n = g.placed++ % 8; g.total++; g.sync(lv.getServer()); }
        return defaultBlockState().setValue(FACING, f).setValue(NUM, n);
    }

    @Override
    public InteractionResult use(BlockState st, Level lv, BlockPos pos, Player pl, InteractionHand h, BlockHitResult hit) {
        if (lv.isClientSide) return InteractionResult.SUCCESS;
        if (pl instanceof ServerPlayer sp && lv instanceof ServerLevel sl) {
            lv.removeBlock(pos, false);
            Slender.collect(sp, sl, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level lv, BlockPos pos, BlockState st, Player pl) {
        if (!lv.isClientSide && pl instanceof ServerPlayer sp && lv instanceof ServerLevel sl && !pl.isCreative()) Slender.collect(sp, sl, pos);
        super.playerWillDestroy(lv, pos, st, pl);
    }
}
