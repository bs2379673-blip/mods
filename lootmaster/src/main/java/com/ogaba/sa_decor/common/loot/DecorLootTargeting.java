// STUB SOLO PARA COMPILAR (no se incluye en el jar): firmas copiadas de sa_decor 1.0.3; en el juego se usan las clases reales.
package com.ogaba.sa_decor.common.loot;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class DecorLootTargeting {
    public static Optional<Target> findLootContainer(Level level, BlockPos hitPos) { throw new UnsupportedOperationException(); }
    public static void clearTarget(Level level, Target target) { throw new UnsupportedOperationException(); }
    public static void saveTarget(Level level, Target target) { throw new UnsupportedOperationException(); }
    public static boolean isUnsupportedDoubleChest(BlockState state) { throw new UnsupportedOperationException(); }
    public record Target(BlockPos pos, BlockEntity blockEntity, DecorLootContainer lootContainer, Container container, DecorLootContainerData data) { }
}
