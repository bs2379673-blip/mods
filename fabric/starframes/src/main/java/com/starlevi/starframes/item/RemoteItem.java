package com.starlevi.starframes.item;

import com.starlevi.starframes.channel.Channels;
import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * El control remoto: se toca una pantalla para vincularlo a su canal; despues, clic derecho en el aire
 * abre los controles (play, pausa, adelantar, lista...) desde donde sea.
 */
public class RemoteItem extends Item {
    public RemoteItem(Properties p) {
        super(p);
    }

    public static String channel(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("canal", "");
    }

    public static void link(ItemStack stack, String channel) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putString("canal", channel);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            String ch = channel(player.getItemInHand(hand));
            if (ch.isEmpty()) {
                sp.displayClientMessage(Component.literal("Tocá una pantalla con el control para vincularlo"), true);
            } else {
                Channels.getOrCreate(ch);
                Channels.join(sp);
                ServerPlayNetworking.send(sp, new Net.OpenRemote(ch));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String ch = channel(stack);
        out.accept(ch.isEmpty() ? Component.literal("Tocá una pantalla para vincularlo").withStyle(ChatFormatting.GRAY)
                : Component.literal("Canal: " + ch).withStyle(ChatFormatting.AQUA));
    }
}
