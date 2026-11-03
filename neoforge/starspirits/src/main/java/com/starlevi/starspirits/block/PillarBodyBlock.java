package com.starlevi.starspirits.block;

import com.starlevi.starspirits.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Parte invisible del cuerpo del Pilar Runico (solo choca): los clics van a la base. */
public class PillarBodyBlock extends Block {
    public PillarBodyBlock(Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private static BlockPos base(Level level, BlockPos pos) {
        for (int i = 1; i <= PillarBlock.HEIGHT; i++) {
            BlockPos b = pos.below(i);
            if (level.getBlockState(b).is(ModRegistry.PILLAR.get())) {
                return b;
            }
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack st, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        BlockPos b = base(level, pos);
        return b != null && PillarBlock.interact(st, level, b, player) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos b = base(level, pos);
        if (b != null) {
            return level.getBlockState(b).useWithoutItem(level, player, hit.withPosition(b));
        }
        return InteractionResult.PASS;
    }
}
