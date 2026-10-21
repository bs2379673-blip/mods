package com.tunombre.jefes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Para administradores: clic derecho en el suelo = invocar el jefe elegido; agachado + clic en el aire = cambiar de jefe. */
public class InvocadorItem extends Item {
    public InvocadorItem(Properties p) { super(p); }

    private static String chosen(ItemStack st) {
        CompoundTag t = st.getTag();
        String id = t == null ? "" : t.getString("Boss");
        if (!Bosses.SERVER.containsKey(id)) id = Bosses.SERVER.isEmpty() ? "" : Bosses.SERVER.keySet().iterator().next();
        return id;
    }

    @Override
    public Component getName(ItemStack st) {
        String id = chosen(st);
        return Component.translatable("item.jefes.invocador").append(Component.literal(id.isEmpty() ? "" : " (" + id + ")"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player pl, InteractionHand hand) {
        ItemStack st = pl.getItemInHand(hand);
        if (level.isClientSide || !pl.isShiftKeyDown() || !(pl instanceof ServerPlayer sp)) return InteractionResultHolder.pass(st);
        List<String> ids = new ArrayList<>(Bosses.SERVER.keySet());
        if (ids.isEmpty()) return InteractionResultHolder.fail(st);
        String next = ids.get((ids.indexOf(chosen(st)) + 1) % ids.size());
        st.getOrCreateTag().putString("Boss", next);
        sp.displayClientMessage(Component.literal("Jefe: §f" + next), true);
        return InteractionResultHolder.success(st);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel().isClientSide || !(c.getLevel() instanceof ServerLevel lv) || !(c.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!sp.hasPermissions(2)) { sp.displayClientMessage(Component.literal("§cSolo para administradores."), true); return InteractionResult.FAIL; }
        if (sp.isShiftKeyDown()) return InteractionResult.PASS;
        var p = c.getClickedPos().relative(c.getClickedFace());
        JefeEntity j = Jefes.spawn(lv, chosen(c.getItemInHand()), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot() + 180F);
        if (j == null) return InteractionResult.FAIL;
        sp.displayClientMessage(Component.literal("Jefe invocado. Usa el mando para controlarlo."), true);
        return InteractionResult.CONSUME;
    }
}
