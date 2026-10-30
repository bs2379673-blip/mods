package com.annis.npc.item;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Varita de NPC: clic derecho en un NPC abre su editor; clic derecho en un bloque crea un NPC nuevo
 * y abre su editor.
 */
public class NpcWandItem extends Item {
    public NpcWandItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (!(entity instanceof AnnisNpcEntity npc)) {
            return InteractionResult.PASS;
        }
        if (!user.level().isClientSide && user instanceof ServerPlayer player) {
            if (player.hasPermissions(2)) {
                NpcNetworking.openEditor(player, npc);
            } else {
                player.displayClientMessage(Component.literal("No tenes permiso para editar NPC"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("No tenes permiso para crear NPC"), true);
            return InteractionResult.FAIL;
        }
        AnnisNpcEntity npc = ModEntities.NPC.create(world);
        if (npc == null) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        float yaw = player.getYRot() + 180.0f;
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        world.addFreshEntity(npc);
        NpcNetworking.openEditor(player, npc);
        return InteractionResult.SUCCESS;
    }
}
