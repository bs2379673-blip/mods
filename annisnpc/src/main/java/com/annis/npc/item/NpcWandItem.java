package com.annis.npc.item;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Varita de NPC: clic derecho en un NPC abre su editor; clic derecho en un bloque crea un NPC nuevo
 * y abre su editor.
 */
public class NpcWandItem extends Item {
    public NpcWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof AnnisNpcEntity npc)) {
            return ActionResult.PASS;
        }
        if (!user.getWorld().isClient && user instanceof ServerPlayerEntity player) {
            if (player.hasPermissionLevel(2)) {
                NpcNetworking.openEditor(player, npc);
            } else {
                player.sendMessage(Text.literal("No tenes permiso para editar NPC"), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return ActionResult.PASS;
        }
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("No tenes permiso para crear NPC"), true);
            return ActionResult.FAIL;
        }
        AnnisNpcEntity npc = ModEntities.NPC.create(world);
        if (npc == null) {
            return ActionResult.FAIL;
        }
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        float yaw = player.getYaw() + 180.0f;
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        world.spawnEntity(npc);
        NpcNetworking.openEditor(player, npc);
        return ActionResult.SUCCESS;
    }
}
