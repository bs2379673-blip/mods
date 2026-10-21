package com.tunombre.obscurenpc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class NpcItems {
    private NpcItems() { }

    public static void putTemplate(ItemStack st, NpcData d, String motor) {
        CompoundTag t = st.getOrCreateTag();
        t.putString("Template", d.toJson());
        t.putString("Motor", motor);
    }

    @Nullable
    public static NpcData getTemplate(ItemStack st) {
        if (st.isEmpty() || !st.hasTag() || !st.getTag().contains("Template")) return null;
        return NpcData.fromJson(st.getTag().getString("Template"));
    }

    public static String motor(ItemStack st) { return st.hasTag() && st.getTag().contains("Motor") ? st.getTag().getString("Motor") : "ef"; }

    private static boolean allowed(Player p) { return p.hasPermissions(2) || p.isCreative(); }

    // ------------------------------------------------------------------ Creador ----
    public static class CreatorItem extends Item {
        public CreatorItem(Properties p) { super(p); }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            Level level = c.getLevel();
            Player p = c.getPlayer();
            if (p == null || !allowed(p)) return InteractionResult.FAIL;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            ItemStack st = c.getItemInHand();
            NpcData d = getTemplate(st);
            if (d == null) d = new NpcData();
            BlockPos pos = c.getClickedPos().relative(c.getClickedFace());
            Actor a = ObscureNpc.create((ServerLevel) level, motor(st), d.copy());
            if (a == null) return InteractionResult.FAIL;
            float yaw = p.getYRot() + 180F;
            a.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0F);
            a.setYHeadRot(yaw);
            a.setYBodyRot(yaw);
            a.data().giro = yaw;
            a.finalizeSpawn((ServerLevel) level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            ((ServerLevel) level).addFreshEntity(a);
            p.displayClientMessage(Component.literal("NPC creado (" + (a.isEf() ? "Epic Fight" : "huesos normales") + "). Usa el Editor de NPC sobre él para configurarlo."), true);
            return InteractionResult.CONSUME;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (p.isShiftKeyDown() && allowed(p)) {
                if (!level.isClientSide) {
                    NpcData d = getTemplate(st);
                    String m = motor(st).equals("ef") ? "normal" : "ef";
                    putTemplate(st, d != null ? d : new NpcData(), m);
                    p.displayClientMessage(Component.literal("Motor del Creador: " + (m.equals("ef") ? "Epic Fight (huesos de EF)" : "huesos normales")), true);
                }
                return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
            }
            return InteractionResultHolder.pass(st);
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag f) {
            NpcData d = getTemplate(st);
            tip.add(Component.literal("Click derecho en un bloque: crea un NPC").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Agachado + click en el aire: cambia de motor").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Motor: " + (motor(st).equals("ef") ? "Epic Fight" : "normal") + (d != null ? " · plantilla: " + d.nombre : " · sin plantilla")).withStyle(ChatFormatting.AQUA));
        }
    }

    // ------------------------------------------------------------------ Editor ----
    public static class EditorItem extends Item {
        public EditorItem(Properties p) { super(p); }

        @Override
        public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity target, InteractionHand hand) {
            if (!(target instanceof Actor a)) return InteractionResult.PASS;
            if (p.level().isClientSide) return InteractionResult.SUCCESS;
            if (!(p instanceof ServerPlayer sp) || !allowed(p)) return InteractionResult.FAIL;
            if (p.isShiftKeyDown()) {
                putTemplate(st, a.data(), a.isEf() ? "ef" : "normal");
                p.displayClientMessage(Component.literal("Plantilla de " + a.data().nombre + " copiada al ítem"), true);
            } else Editor.open(sp, a);
            return InteractionResult.CONSUME;
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            Player p = c.getPlayer();
            if (p == null || !allowed(p)) return InteractionResult.FAIL;
            if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
            if (c.getLevel().getBlockEntity(c.getClickedPos()) instanceof SpawnerEntity be && p instanceof ServerPlayer sp) {
                NpcData t = getTemplate(c.getItemInHand());
                if (p.isShiftKeyDown() && t != null) {
                    be.template = t.copy();
                    be.motor = motor(c.getItemInHand());
                    be.setChanged();
                    p.displayClientMessage(Component.literal("Plantilla (" + t.nombre + ") aplicada al spawner"), true);
                } else Editor.openSpawner(sp, c.getClickedPos());
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag f) {
            NpcData d = getTemplate(st);
            tip.add(Component.literal("Click derecho en un NPC: abrir el editor").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Agachado + click en un NPC: copiar su plantilla").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Agachado + click en un spawner: pegar la plantilla").withStyle(ChatFormatting.GRAY));
            if (d != null) tip.add(Component.literal("Plantilla guardada: " + d.nombre).withStyle(ChatFormatting.AQUA));
        }
    }
}
