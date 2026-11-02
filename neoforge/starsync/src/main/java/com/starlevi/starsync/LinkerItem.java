package com.starlevi.starsync;

import me.srrapero720.waterframes.common.block.entity.DisplayTile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Vinculador: clic derecho en una pantalla de WaterFrames la agrega al grupo del vinculador (o la saca
 * si ya estaba). Agachado + clic derecho al aire: grupo nuevo. Agachado + clic en una pantalla:
 * el vinculador pasa a ese grupo.
 */
public final class LinkerItem extends Item {
    public LinkerItem(Properties p) {
        super(p);
    }

    static String groupOf(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag().getString("grupo");
    }

    static void setGroup(ItemStack s, String id) {
        CompoundTag t = new CompoundTag();
        t.putString("grupo", id);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof DisplayTile)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel sl) || ctx.getPlayer() == null) {
            return InteractionResult.SUCCESS; // en el cliente: que no se abra la pantalla de WaterFrames
        }
        Player p = ctx.getPlayer();
        Groups all = Groups.get();
        if (all == null) {
            return InteractionResult.FAIL;
        }
        if (p.isShiftKeyDown()) {
            Groups.Group g = all.groupOf(sl.dimension(), ctx.getClickedPos());
            if (g != null) {
                setGroup(stack, g.id);
                p.displayClientMessage(Component.literal("Vinculador en " + g.id + " (" + g.members.size() + " pantallas)").withStyle(ChatFormatting.AQUA), true);
                return InteractionResult.SUCCESS;
            }
        }
        String id = groupOf(stack);
        Groups.Group g = id == null || id.isEmpty() ? null : all.group(id);
        if (g == null) {
            g = all.create();
            setGroup(stack, g.id);
        }
        boolean in = all.toggle(g, sl.dimension(), ctx.getClickedPos());
        p.displayClientMessage(Component.literal((in ? "Pantalla agregada a " : "Pantalla quitada de ") + g.id + " (" + g.members.size() + " pantallas)")
                .withStyle(in ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        if (g.members.isEmpty()) {
            all.remove(g.id);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (!p.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && Groups.get() != null) {
            Groups.Group g = Groups.get().create();
            setGroup(stack, g.id);
            p.displayClientMessage(Component.literal("Grupo nuevo: " + g.id + ". Ahora hace clic en las pantallas.").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String id = groupOf(stack);
        lines.add(Component.literal(id == null || id.isEmpty() ? "Sin grupo" : "Grupo: " + id).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Clic en una pantalla: agregar / quitar").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Agachado + clic al aire: grupo nuevo").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Agachado + clic en una pantalla: usar su grupo").withStyle(ChatFormatting.GRAY));
    }
}
