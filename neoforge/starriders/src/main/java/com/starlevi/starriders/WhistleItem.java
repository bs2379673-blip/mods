package com.starlevi.starriders;

import com.starlevi.starriders.server.Mounts;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;

import java.util.List;
import java.util.UUID;

/** Silbato ligado a una montura y a su dueno: la llama y la guarda. */
public class WhistleItem extends Item {
    public WhistleItem(Properties p) {
        super(p);
    }

    /** Un silbato para esta montura de este jugador. */
    public static ItemStack make(Player owner, RiderData.Mount m) {
        ItemStack st = new ItemStack(ModRegistry.WHISTLE.get());
        CompoundTag t = new CompoundTag();
        t.putUUID("montura", m.id());
        t.putString("tipo", m.type());
        t.putUUID("dueno", owner.getUUID());
        t.putString("dueno_nombre", owner.getGameProfile().getName());
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        st.set(DataComponents.CUSTOM_NAME, Component.literal("Silbato de " + Mounts.displayName(m.type()))
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        return st;
    }

    private static CompoundTag tag(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(st);
        }
        CompoundTag t = tag(st);
        if (!t.hasUUID("montura") || !(player instanceof ServerPlayer sp)) {
            player.displayClientMessage(Component.literal("Este silbato no está ligado a ninguna montura"), true);
            return InteractionResultHolder.fail(st);
        }
        UUID owner = t.hasUUID("dueno") ? t.getUUID("dueno") : null;
        if (!player.getUUID().equals(owner)) {
            player.displayClientMessage(Component.literal("Este silbato es de " + t.getString("dueno_nombre")), true);
            return InteractionResultHolder.fail(st);
        }
        Mounts.whistle(sp, t.getUUID("montura"));
        player.getCooldowns().addCooldown(this, 30);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        CompoundTag t = tag(st);
        if (t.contains("dueno_nombre")) {
            lines.add(Component.literal("Montura de " + t.getString("dueno_nombre")).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal("Clic derecho: llamarla o guardarla").withStyle(ChatFormatting.DARK_GRAY));
        if ("guardian".equals(t.getString("tipo"))) {
            lines.add(Component.literal("Modelo: \"Tree Heretic Keeper\" de Vasian-Digital3D (CC BY 4.0)").withStyle(ChatFormatting.DARK_GRAY));
        }
        if ("serpiente".equals(t.getString("tipo"))) {
            lines.add(Component.literal("Modelo: \"Snake\" de Vasian-Digital3D (CC BY 4.0)").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }
}
