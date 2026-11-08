package com.leon.saintsdragons.common.item;

import net.minecraft.world.InteractionResult;

import net.minecraft.nbt.CompoundTag;

import com.leon.saintsdragons.client.renderer.item.MossbackItemRenderer;
import com.leon.saintsdragons.common.registry.ModEntities;
import com.leon.saintsdragons.server.entity.dragons.Mossback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class MossbackItem extends Item implements GeoItem {
    private static final String BABY_TAG = "BabyMossback";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mossback.idle");
    private static final RawAnimation BABY_IDLE = RawAnimation.begin().thenLoop("baby_mossback.animation.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MossbackItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            Mossback mossback = ModEntities.MOSSBACK.get().create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            if (mossback != null) {
                mossback.setBaby(isBaby(stack));
                Vec3 look = player.getLookAngle();
                Vec3 spawn = player.getEyePosition().add(look.scale(0.65D));
                mossback.snapTo(spawn.x, spawn.y - 0.25D, spawn.z, player.getYRot(), 0.0F);
                mossback.setDeltaMovement(look.scale(1.25D).add(0.0D, 0.18D, 0.0D));
                mossback.markThrown();
                serverLevel.addFreshEntity(mossback);
                player.playSound(SoundEvents.SNOWBALL_THROW, 0.6F, 0.85F + player.getRandom().nextFloat() * 0.25F);
                stack.shrink(1);
            }
        }

        return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("idle", 4, state -> {
            ItemStack stack = state.getData(com.leon.saintsdragons.util.compat.SDTickets.ITEMSTACK);
            state.setAndContinue(isBaby(stack) ? BABY_IDLE : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    public static boolean isBaby(ItemStack stack) {
        return stack != null && com.leon.saintsdragons.util.compat.Compat.hasTag(stack) && com.leon.saintsdragons.util.compat.Compat.getTag(stack).getBooleanOr(BABY_TAG, false);
    }

    public static void setBaby(ItemStack stack, boolean baby) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = com.leon.saintsdragons.util.compat.Compat.getOrCreateTag(stack);
        if (baby) {
            tag.putBoolean(BABY_TAG, true);
        } else {
            tag.remove(BABY_TAG);
        }
        com.leon.saintsdragons.util.compat.Compat.setTag(stack, tag);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
    private static final class MossbackForgeRendererHolder {
        private static MossbackItemRenderer renderer;

        private static MossbackItemRenderer renderer() {
            if (renderer == null) {
                renderer = new MossbackItemRenderer();
            }
            return renderer;
        }
    }

    // port 1.21.11 / GeckoLib 5: el renderer se pide aca (solo en el cliente)
    @Override
    public void createGeoRenderer(Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
            private com.leon.saintsdragons.client.renderer.item.MossbackItemRenderer renderer;

            @Override
            public software.bernie.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new com.leon.saintsdragons.client.renderer.item.MossbackItemRenderer();
                }
                return this.renderer;
            }
        });
    }
}
