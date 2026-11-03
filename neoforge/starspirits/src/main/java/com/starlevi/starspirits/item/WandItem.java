package com.starlevi.starspirits.item;

import com.starlevi.starspirits.server.Homes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Varita de espiritus: con clic derecho en bloques marcas las puntas de un area (tu casa, un jardin...)
 * y con clic derecho al aire abris el menu: elegis que espiritus te siguen (2) y cuales viven en cada area.
 */
public class WandItem extends Item {
    public WandItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                Homes.openMenu(sp, true);
            } else {
                Homes.mark(sp, ctx.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Homes.openMenu(sp, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Clic derecho en un bloque: marcar las puntas de un área").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Clic derecho al aire: tus espíritus y tus áreas").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }
}
