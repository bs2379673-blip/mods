package com.starlevi.starframes.block;

import com.mojang.serialization.MapCodec;
import com.starlevi.starframes.StarFrames;
import com.starlevi.starframes.channel.Channels;
import com.starlevi.starframes.item.RemoteItem;
import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La pantalla: una placa finita contra la pared (o parada en el piso, mirando a quien la pone). El
 * video se dibuja adelante, del tamaño que se le configure. Clic derecho abre su menu; con el control
 * remoto en la mano, lo vincula a su canal.
 */
public class ScreenBlock extends BaseEntityBlock {
    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16), SOUTH = Block.box(0, 0, 0, 16, 16, 1),
            EAST = Block.box(0, 0, 0, 1, 16, 16), WEST = Block.box(15, 0, 0, 16, 16, 16);

    public ScreenBlock(Properties p) {
        super(p);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (s.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScreenBlockEntity be && be.channel().isEmpty()) {
            be.set("canal", Channels.create().id);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.getItem() instanceof RemoteItem) {
            if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ScreenBlockEntity be) {
                RemoteItem.link(stack, be.channel());
                sp.displayClientMessage(Component.literal("Control vinculado al canal " + be.channel()), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            if (!Channels.allowed(sp)) {
                sp.displayClientMessage(Component.literal("Necesitás permiso de administrador para configurar pantallas"), true);
            } else if (level.getBlockEntity(pos) instanceof ScreenBlockEntity be) {
                if (be.channel().isEmpty()) {
                    be.set("canal", Channels.create().id);
                }
                Channels.getOrCreate(be.channel());
                ServerPlayNetworking.send(sp, new Net.OpenScreen(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    static {
        StarFrames.LOGGER.debug("pantalla lista");
    }
}
