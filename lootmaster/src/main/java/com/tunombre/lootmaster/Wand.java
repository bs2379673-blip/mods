package com.tunombre.lootmaster;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Varita de seleccion (estilo WorldEdit): clic izquierdo = punto 1, clic derecho = punto 2, aire / agachado = panel. */
public final class Wand {
    public static final class Sel { public String dim = ""; public BlockPos a, b; }

    private static final Map<UUID, Sel> SEL = new HashMap<>();

    private Wand() { }

    public static Sel get(ServerPlayer p) { return SEL.get(p.getUUID()); }

    public static void clear() { SEL.clear(); }

    public static JsonObject json(ServerPlayer p) {
        JsonObject o = new JsonObject();
        Sel s = p == null ? null : SEL.get(p.getUUID());
        o.addProperty("dim", s == null ? "" : s.dim);
        if (s != null && s.a != null) o.add("a", arr(s.a));
        if (s != null && s.b != null) o.add("b", arr(s.b));
        return o;
    }

    private static JsonArray arr(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static boolean holding(Player p) { return p.getMainHandItem().getItem() == LootMaster.WAND.get(); }

    private static void set(ServerPlayer p, BlockPos pos, boolean first) {
        Sel s = SEL.computeIfAbsent(p.getUUID(), k -> new Sel());
        String dim = p.level().dimension().location().toString();
        if (!dim.equals(s.dim)) { s.a = null; s.b = null; s.dim = dim; }
        if (first) s.a = pos.immutable(); else s.b = pos.immutable();
        p.displayClientMessage(Component.literal((first ? "Punto 1: " : "Punto 2: ") + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + (s.a != null && s.b != null ? " · zona " + (Math.abs(s.a.getX() - s.b.getX()) + 1) + "×" + (Math.abs(s.a.getZ() - s.b.getZ()) + 1) : "")), true);
        Net.toClient(p, "sel", json(p).toString());
    }

    @SubscribeEvent
    public static void onLeft(PlayerInteractEvent.LeftClickBlock e) {
        if (!holding(e.getEntity())) return;
        e.setCanceled(true);
        if (e.getEntity() instanceof ServerPlayer p && e.getHand() == InteractionHand.MAIN_HAND && p.hasPermissions(2)) set(p, e.getPos(), true);
    }

    @SubscribeEvent
    public static void onRight(PlayerInteractEvent.RightClickBlock e) {
        if (!holding(e.getEntity()) || e.getHand() != InteractionHand.MAIN_HAND) return;
        e.setUseBlock(Event.Result.DENY);
        e.setUseItem(Event.Result.DENY);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getEntity() instanceof ServerPlayer p && p.hasPermissions(2)) {
            if (p.isShiftKeyDown()) openPanel(p); else set(p, e.getPos(), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SEL.remove(e.getEntity().getUUID()); }

    static void openPanel(ServerPlayer p) {
        Net.toClient(p, "tab", "Varita");
        Ops.open(p);
    }

    public static ServerLevel levelOf(ServerPlayer p, Sel s) {
        for (ServerLevel lv : p.getServer().getAllLevels()) if (lv.dimension().location().toString().equals(s.dim)) return lv;
        return null;
    }

    public static final class WandItem extends Item {
        public WandItem(Properties pr) { super(pr); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player pl, InteractionHand hand) {
            if (!level.isClientSide && pl instanceof ServerPlayer p) {
                if (!p.hasPermissions(2)) p.displayClientMessage(Component.literal("Solo para operadores."), true);
                else openPanel(p);
            }
            return InteractionResultHolder.sidedSuccess(pl.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack st, Level lv, List<Component> tip, TooltipFlag fl) {
            tip.add(Component.literal("Clic izquierdo en un bloque: punto 1"));
            tip.add(Component.literal("Clic derecho en un bloque: punto 2"));
            tip.add(Component.literal("Clic en el aire o agachado + clic: abrir el panel"));
        }
    }
}
