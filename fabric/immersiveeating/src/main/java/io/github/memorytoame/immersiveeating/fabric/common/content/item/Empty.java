package io.github.memorytoame.immersiveeating.fabric.common.content.item;

import io.github.memorytoame.immersiveeating.fabric.common.content.definition.FoodDefinitionManager;
import io.github.memorytoame.immersiveeating.fabric.init.ModItems;
import io.github.memorytoame.immersiveeating.fabric.unique.UniqueItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class Empty extends Item implements GeoItem {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static Consumer<ItemStack> clientUseReequipHandler = ignored -> {};
   private static Supplier<ItemStack> clientAnimationStackSupplier = () -> ItemStack.EMPTY;
   private static Runnable clientAnimationStackClearHandler = () -> {};
   private static Runnable clientHotbarUnlockHandler = () -> {};
   private static Supplier<Item> clientGetMainHandItem = () -> null;
   private static Supplier<UUID> clientAnimationSessionSupplier = () -> null;
   private static Supplier<Long> clientAnimationControllerSupplier = () -> Long.MAX_VALUE;
   private static Consumer<Identifier> clientSoundHandler = ignored -> {};
   private static Runnable clientSoundStopHandler = () -> {};
   private static BiConsumer<InteractionHand, Item> clientCustomUseHandler = (ignoredHand, ignoredItem) -> {};
   private static BiConsumer<InteractionHand, UUID> clientFinishUseHandler = (ignoredHand, ignoredSession) -> {};
   private final List<AnimationController<Empty>> reg_controllers = new ArrayList<>(3);

   public Empty(Properties pProperties) {
      super(pProperties);
      GeoItem.registerSyncedAnimatable(this);
   }

   public static void setClientHooks(
      Consumer<ItemStack> useReequipHandler,
      Runnable cameraShakeHandler,
      Supplier<ItemStack> animationStackSupplier,
      Runnable animationStackClearHandler,
      Runnable hotbarUnlockHandler,
      Supplier<Item> mainHandItem,
      Supplier<UUID> animationSessionSupplier,
      Supplier<Long> animationControllerSupplier
   ) {
      clientUseReequipHandler = useReequipHandler;
      clientAnimationStackSupplier = animationStackSupplier;
      clientAnimationStackClearHandler = animationStackClearHandler;
      clientHotbarUnlockHandler = hotbarUnlockHandler;
      clientGetMainHandItem = mainHandItem;
      clientAnimationSessionSupplier = animationSessionSupplier;
      clientAnimationControllerSupplier = animationControllerSupplier;
   }

   public static void setClientSoundHooks(Consumer<Identifier> soundHandler, Runnable soundStopHandler) {
      clientSoundHandler = soundHandler;
      clientSoundStopHandler = soundStopHandler;
   }

   public static void setClientNetworkHooks(
      BiConsumer<InteractionHand, Item> customUseHandler,
      BiConsumer<InteractionHand, UUID> finishUseHandler
   ) {
      clientCustomUseHandler = customUseHandler;
      clientFinishUseHandler = finishUseHandler;
   }

   public static void stopEatAnimation(long instanceId) {
      clientSoundStopHandler.run();
      Empty empty = ModItems.EMPTY;
      AnimationController<?> controller = empty.getAnimatableInstanceCache()
         .getManagerForId(instanceId)
         .getAnimationControllers()
         .get("eat");
      if (controller != null) {
         controller.stopTriggeredAnimation();
      }
   }

   public static boolean isEatAnimationPlaying() {
      return isClientAnimationPlaying(clientAnimationStackSupplier.get(), "eat");
   }

   public static boolean isClientAnimationPlaying(ItemStack stack, String controllerName) {
      if (stack != null && !stack.isEmpty()) {
         long instanceId = resolveControllerId(stack);
         Empty empty = ModItems.EMPTY;
         AnimationController<?> controller = empty.getAnimatableInstanceCache()
            .getManagerForId(instanceId)
            .getAnimationControllers()
            .get(controllerName);
         return controller != null && controller.isPlayingTriggeredAnimation();
      } else {
         return false;
      }
   }

   public static void stopClientAnimation(ItemStack stack, String controllerName) {
      if (stack != null && !stack.isEmpty()) {
         long instanceId = resolveControllerId(stack);
         AnimationController<?> controller = ModItems.EMPTY.getAnimatableInstanceCache()
            .getManagerForId(instanceId)
            .getAnimationControllers()
            .get(controllerName);
         if (controller != null) {
            controller.stopTriggeredAnimation();
         }
      }
   }

   public static void triggerClientAnimation(ItemStack stack, String controllerName) {
      if (stack != null && !stack.isEmpty()) {
         Empty empty = ModItems.EMPTY;
         long instanceId = resolveControllerId(stack);
         AnimationController<?> controller = empty.getAnimatableInstanceCache()
            .getManagerForId(instanceId)
            .getAnimationControllers()
            .get(controllerName);
         if (controller != null) {
            clientSoundStopHandler.run();
            // triggerAnimation restarts it from the beginning (no reset: it may be called while GeckoLib updates controllers)
            controller.triggerAnimation(controllerName);
         }
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      this.reg_controllers.add(this.createControl("eat"));
      this.reg_controllers.add(this.createControl("switchin"));
      this.reg_controllers.add(this.createControl("switchout"));

      for (AnimationController<Empty> controller : this.reg_controllers) {
         controller.setSoundKeyframeHandler(
            keyFrames -> {
               ItemStack animationStack = clientAnimationStackSupplier.get();
               if (animationStack != null && !animationStack.isEmpty()) {
                  Identifier itemId = BuiltInRegistries.ITEM.getKey(animationStack.getItem());
                  String soundKey = keyFrames.keyframeData().getSound();
                  if ("pickup".equals(soundKey)) {
                     clientSoundHandler.accept(Identifier.fromNamespaceAndPath("minecraft", "entity.item.pickup"));
                  }

                  Identifier soundId = FoodDefinitionManager.getSound(itemId.toString(), soundKey);
                  if (soundId != null && clientGetMainHandItem.get() != null) {
                     if (UniqueItem.UNIQUE_ITEM_MAP.get(BuiltInRegistries.ITEM.getKey(clientGetMainHandItem.get())) != null) {
                        Item child_item = BuiltInRegistries.ITEM
                           .getValue(UniqueItem.UNIQUE_ITEM_MAP.get(BuiltInRegistries.ITEM.getKey(clientGetMainHandItem.get())));
                        if (child_item != null) {
                           clientCustomUseHandler.accept(InteractionHand.MAIN_HAND, child_item);
                        }
                     } else {
                        clientSoundHandler.accept(soundId);
                     }
                  }
               }
            }
         );
         controller.setCustomInstructionKeyframeHandler(keyFrames -> {
            if (keyFrames.keyframeData().getInstructions().contains("finished")) {
               ItemStack animationStack = clientAnimationStackSupplier.get();
               // GeckoLib 5 is still advancing this controller here: only end the triggered animation
               // (a reset now would clear its timeline mid-update and crash)
               controller.stopTriggeredAnimation();
               if (animationStack != null && !animationStack.isEmpty()) {
                  clientAnimationStackClearHandler.run();
                  UUID sessionId = clientAnimationSessionSupplier.get();
                  if (sessionId != null) {
                     clientFinishUseHandler.accept(InteractionHand.MAIN_HAND, sessionId);
                  }

                  clientUseReequipHandler.accept(animationStack.copy());
               }

               clientHotbarUnlockHandler.run();
            }
         });
         controllers.add(controller);
      }
   }

   private AnimationController<Empty> createControl(String name) {
      AnimationController<Empty> controller = new AnimationController<>(name, state -> PlayState.STOP);
      return controller.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
   }

   private static long resolveControllerId(ItemStack stack) {
      long stackId = stack != null && !stack.isEmpty() ? GeoItem.getId(stack) : Long.MAX_VALUE;
      return stackId == Long.MAX_VALUE ? clientAnimationControllerSupplier.get() : stackId;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

}

