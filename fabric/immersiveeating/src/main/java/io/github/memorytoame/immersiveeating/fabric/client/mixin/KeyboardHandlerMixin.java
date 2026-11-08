package io.github.memorytoame.immersiveeating.fabric.client.mixin;

import io.github.memorytoame.immersiveeating.fabric.utils.AnimationUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void food$blockHotbarKeys(long windowPointer, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
      if (AnimationUtils.isInputLocked() && !food$isAllowedDuringAnimation(event)) {
         ci.cancel();
      }
   }

   @Unique
   private static boolean food$isAllowedDuringAnimation(net.minecraft.client.input.KeyEvent e) {
      Options options = Minecraft.getInstance().options;
      return options.keyAttack.matches(e)
         || options.keyUp.matches(e)
         || options.keyDown.matches(e)
         || options.keyLeft.matches(e)
         || options.keyRight.matches(e)
         || options.keyJump.matches(e);
   }
}

