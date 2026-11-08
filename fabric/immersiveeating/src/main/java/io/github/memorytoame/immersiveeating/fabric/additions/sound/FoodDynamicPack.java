package io.github.memorytoame.immersiveeating.fabric.additions.sound;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.NotNull;

public class FoodDynamicPack implements PackResources {
   private static final PackMetadataSection METADATA = new PackMetadataSection(
      Component.literal("Provides models and animations."),
      new InclusiveRange<>(PackFormat.of(SharedConstants.RESOURCE_PACK_FORMAT_MAJOR, SharedConstants.RESOURCE_PACK_FORMAT_MINOR))
   );
   private static final PackLocationInfo LOCATION = new PackLocationInfo(
      "food_dynamic", Component.literal("Kaleidoscope ImmersiveEating Resources"), PackSource.BUILT_IN, Optional.empty()
   );

   public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
      if (type != PackType.CLIENT_RESOURCES) {
         return null;
      }

      if (!location.getNamespace().equals("food")) {
         return null;
      }

      if (!location.getPath().equals("sounds.json")) {
         return null;
      }

      String content = createSoundsJson().toString();
      return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
   }

   @NotNull
   public Set<String> getNamespaces(@NotNull PackType type) {
      return type == PackType.CLIENT_RESOURCES ? Set.of("food") : Set.of();
   }

   public void listResources(PackType type, @NotNull String namespace, String prefix, ResourceOutput output) {
      if (type == PackType.CLIENT_RESOURCES) {
         if (namespace.equals("food")) {
            if ("sounds.json".startsWith(prefix)) {
               output.accept(Identifier.fromNamespaceAndPath("food", "sounds.json"), (IoSupplier)() -> {
                  JsonObject json = createSoundsJson();
                  return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
               });
            }
         }
      }
   }

   public IoSupplier<InputStream> getRootResource(String... path) {
      return path.length == 1 && path[0].equals("pack.png") ? () -> FoodDynamicPack.class.getResourceAsStream("/pack.png") : null;
   }

   @SuppressWarnings("unchecked")
   public <T> T getMetadataSection(MetadataSectionType<T> type) {
      return (T)(type == PackMetadataSection.CLIENT_TYPE || type == PackMetadataSection.FALLBACK_TYPE ? METADATA : null);
   }

   public PackLocationInfo location() {
      return LOCATION;
   }

   public String packId() {
      return "food_dynamic";
   }

   private static JsonObject createSoundsJson() {
      return SoundJson.generate(Minecraft.getInstance().getResourceManager());
   }

   public void close() {
   }
}

