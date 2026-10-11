package com.tunombre.saclothing;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.client.render.CuriosLayer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;

final class Installer {
    private static final Logger LOG = LogUtils.getLogger();

    private Installer() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void install(PatchedLivingEntityRenderer renderer) throws Exception {
        // 1) capa: envolver la de Curios que ya exista
        Field f = PatchedLivingEntityRenderer.class.getDeclaredField("patchedLayers");
        f.setAccessible(true);
        Map<Class<?>, PatchedLayer> layers = (Map<Class<?>, PatchedLayer>) f.get(renderer);
        PatchedLayer existing = layers.get(CuriosLayer.class);
        if (!(existing instanceof CurioMeshLayer)) {
            renderer.addPatchedLayerAlways(CuriosLayer.class, new CurioMeshLayer(existing));
        }
        // 2) renderers de Curios de las prendas con malla: que no dibujen si ya lo hace Epic Fight
        int wrapped = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof ArmorItem) || !ArmorMeshes.hasJson(item)) {
                continue;
            }
            Optional<ICurioRenderer> current = CuriosRendererRegistry.getRenderer(item);
            if (current.isEmpty() || current.get() instanceof MeshAwareCurioRenderer) {
                continue;
            }
            final ICurioRenderer original = current.get();
            CuriosRendererRegistry.register(item, () -> new MeshAwareCurioRenderer(original));
            wrapped++;
        }
        if (wrapped > 0) {
            CuriosRendererRegistry.load();
        }
        LOG.info("[saclothing] instalado: capa de Curios {} ; prendas con malla de Epic Fight en Curios: {}",
                existing == null ? "(sin capa previa)" : existing.getClass().getName(), wrapped);
    }
}
