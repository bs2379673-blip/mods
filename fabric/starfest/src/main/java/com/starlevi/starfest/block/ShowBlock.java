package com.starlevi.starfest.block;

import com.mojang.serialization.MapCodec;
import com.starlevi.starfest.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Los bloques de StarFest. Kind dice cual es: el modelo fijo va en el JSON del bloque y lo que se
 * mueve (cabezal, haz de luz, colores) lo dibuja el cliente con el ritmo de la musica.
 */
public class ShowBlock extends BaseEntityBlock {
    public enum Kind { FOCO, REFLECTOR, PISO, CABINA, LANZADOR }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public final Kind kind;

    public ShowBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        if (hasFacing()) {
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, kind == Kind.CABINA ? Direction.NORTH : Direction.UP));
        }
    }

    private boolean hasFacing() {
        return this.kind != Kind.PISO;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new ShowBlock(this.kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        // PISO no tiene orientacion, pero el constructor todavia no conoce "kind" aqui: se agrega siempre
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = this.defaultBlockState();
        return switch (this.kind) {
            case PISO -> s.setValue(FACING, Direction.UP);
            // la cabina mira hacia quien la pone
            case CABINA -> s.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
            // focos y reflectores: en el piso miran arriba, en el techo abajo, en la pared hacia afuera
            default -> s.setValue(FACING, ctx.getClickedFace());
        };
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    private static final VoxelShape[] FOCO = shapes(3, 0, 3, 13, 14, 13);
    private static final VoxelShape[] REFLECTOR = shapes(2, 0, 2, 14, 15, 14);
    private static final VoxelShape[] LANZADOR = shapes(4, 0, 4, 12, 11, 12);
    private static final VoxelShape CABINA = Block.box(0, 0, 0, 16, 15, 16);

    /** Una forma para cada orientacion (como si el bloque "creciera" hacia FACING). */
    private static VoxelShape[] shapes(double x0, double y0, double z0, double x1, double y1, double z1) {
        VoxelShape[] out = new VoxelShape[6];
        for (Direction d : Direction.values()) {
            out[d.ordinal()] = switch (d) {
                case UP -> Block.box(x0, y0, z0, x1, y1, z1);
                case DOWN -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
                case NORTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
                case SOUTH -> Block.box(x0, z0, y0, x1, z1, y1);
                case WEST -> Block.box(16 - y1, x0, z0, 16 - y0, x1, z1);
                case EAST -> Block.box(y0, x0, z0, y1, x1, z1);
            };
        }
        return out;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (this.kind) {
            case FOCO -> FOCO[s.getValue(FACING).ordinal()];
            case REFLECTOR -> REFLECTOR[s.getValue(FACING).ordinal()];
            case LANZADOR -> LANZADOR[s.getValue(FACING).ordinal()];
            case CABINA -> CABINA;
            case PISO -> Block.box(0, 0, 0, 16, 16, 16);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShowBlockEntity(Registry.typeFor(this.kind), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() && this.kind == Kind.LANZADOR) {
            return (l, p, s, be) -> ClientTick.launcher(l, p, (ShowBlockEntity) be);
        }
        return null;
    }

    /** Puente al cliente sin cargar clases de cliente en el servidor. */
    public static final class ClientTick {
        public static Launcher launcher = (l, p, be) -> { };

        public interface Launcher {
            void tick(Level level, BlockPos pos, ShowBlockEntity be);
        }

        static void launcher(Level l, BlockPos p, ShowBlockEntity be) {
            launcher.tick(l, p, be);
        }
    }
}
