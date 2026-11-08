package io.github.memorytoame.immersiveeating.fabric.init;

import io.github.memorytoame.immersiveeating.fabric.KaleidoscopeImmersiveEating;
import io.github.memorytoame.immersiveeating.fabric.common.content.item.Empty;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class ModItems {
   public static final Empty EMPTY = Registry.register(
      BuiltInRegistries.ITEM,
      Identifier.fromNamespaceAndPath(KaleidoscopeImmersiveEating.MOD_ID, "empty"),
      new Empty(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
         Identifier.fromNamespaceAndPath(KaleidoscopeImmersiveEating.MOD_ID, "empty"))))
   );

   public static void init() {}

   private ModItems() {
   }
}

