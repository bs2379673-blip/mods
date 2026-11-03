package com.starlevi.starspirits.item;

import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.server.Caravan;
import com.starlevi.starspirits.server.Wild;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Un espiritu atrapado (todavia salvaje). Hay que llevarlo a un Pilar Runico para que sea tuyo; si
 * lo soltas o lo tiras, se escapa.
 */
public class SpiritItem extends Item {
    public SpiritItem(Properties p) {
        super(p);
    }

    public static ItemStack make(String type) {
        ItemStack st = new ItemStack(ModRegistry.CAUGHT.get());
        CompoundTag t = new CompoundTag();
        t.putString("tipo", type);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        st.set(DataComponents.CUSTOM_NAME, Component.literal("✨ " + Caravan.displayName(type) + " (salvaje)")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
        return st;
    }

    public static String typeOf(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag().getString("tipo");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        String type = typeOf(st);
        if (!level.isClientSide && type != null && !type.isEmpty()) {
            // soltarlo: sale corriendo
            Vec3 at = player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5));
            Wild.release((ServerLevel) level, at, type);
            st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    /** Si lo tiran al piso, se escapa enseguida. */
    @Override
    public boolean onEntityItemUpdate(ItemStack st, ItemEntity entity) {
        if (!entity.level().isClientSide && entity.getAge() > 8) {
            String type = typeOf(st);
            if (type != null && !type.isEmpty()) {
                for (int i = 0; i < st.getCount(); i++) {
                    Wild.release((ServerLevel) entity.level(), entity.position(), type);
                }
            }
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Llevalo a un Pilar Rúnico para que sea tuyo").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Si lo soltás, se escapa").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }
}
