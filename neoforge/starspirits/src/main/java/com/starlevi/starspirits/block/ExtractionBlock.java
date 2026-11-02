package com.starlevi.starspirits.block;

import com.mojang.serialization.MapCodec;
import com.starlevi.starspirits.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pilar de piedra con flores y una semilla brillante: al tocarlo, sale un espiritu. */
public class ExtractionBlock extends BaseEntityBlock {
    public static final MapCodec<ExtractionBlock> CODEC = simpleCodec(ExtractionBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(4, 4, 4, 12, 16, 12),
            Block.box(2, 16, 2, 14, 20, 14));

    public ExtractionBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExtractionBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            be = new ExtractionBlockEntity(pos, state);
            level.setBlockEntity(be);
        }
        if (be instanceof ExtractionBlockEntity e && player instanceof ServerPlayer sp) {
            e.use(sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.EXTRACTION_BE.get(), level.isClientSide ? ExtractionBlockEntity::clientTick : ExtractionBlockEntity::serverTick);
    }
}
