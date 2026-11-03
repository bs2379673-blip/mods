package com.starlevi.starspirits.block;

import com.mojang.serialization.MapCodec;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.item.SpiritItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pilar Runico: una piedra gigante con una runa. Aca se vinculan los espiritus atrapados (pasan a
 * ser tuyos y te siguen). Es la base; arriba tiene bloques invisibles que forman el cuerpo.
 */
public class PillarBlock extends BaseEntityBlock {
    public static final MapCodec<PillarBlock> CODEC = simpleCodec(PillarBlock::new);
    /** Alto del pilar en bloques (la base cuenta). */
    public static final int HEIGHT = 7;

    public PillarBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PillarBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack st, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        return interact(st, level, pos, player) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("🗿 Traé un espíritu atrapado para vincularlo con vos").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si tiene un espiritu atrapado en la mano, empieza el ritual. */
    static boolean interact(ItemStack st, Level level, BlockPos base, Player player) {
        if (!(st.getItem() instanceof SpiritItem)) {
            return false;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(base) instanceof PillarBlockEntity be) {
            String type = SpiritItem.typeOf(st);
            if (type != null && !type.isEmpty() && be.startRitual(sp, type)) {
                st.shrink(1);
            }
        }
        return true;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.PILLAR_BE.get(), PillarBlockEntity::serverTick);
    }
}
