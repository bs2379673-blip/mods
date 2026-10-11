package com.tunombre.saclothing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;

/** Busca/carga la malla de Epic Fight de una prenda: assets/&lt;ns&gt;/animmodels/armor/&lt;item&gt;.json */
final class ArmorMeshes {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<Item> BROKEN = new HashSet<>();

    private ArmorMeshes() {
    }

    static ResourceLocation jsonLocation(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return new ResourceLocation(key.getNamespace(), "animmodels/armor/" + key.getPath() + ".json");
    }

    static boolean hasJson(Item item) {
        if (!(item instanceof ArmorItem) || BROKEN.contains(item) || ForgeRegistries.ITEMS.getKey(item) == null) {
            return false;
        }
        return Minecraft.getInstance().getResourceManager().getResource(jsonLocation(item)).isPresent();
    }

    /** Malla lista para dibujar o null si la prenda no tiene JSON (o fallo al leerlo). */
    static SkinnedMesh get(Item item) {
        if (!hasJson(item)) {
            return null;
        }
        SkinnedMesh mesh = WearableItemLayer.getCachedModel(item);
        if (mesh != null) {
            return mesh;
        }
        try {
            JsonAssetLoader loader = new JsonAssetLoader(Minecraft.getInstance().getResourceManager(), jsonLocation(item));
            mesh = loader.loadSkinnedMesh(SkinnedMesh::new);
            WearableItemLayer.putModel(ForgeRegistries.ITEMS.getKey(item), mesh);
            return mesh;
        } catch (Throwable t) {
            BROKEN.add(item);
            LOG.error("[saclothing] no se pudo cargar la malla de " + ForgeRegistries.ITEMS.getKey(item), t);
            return null;
        }
    }

    static void draw(SkinnedMesh mesh, Armature armature, PoseStack poseStack, MultiBufferSource buffers, int light, OpenMatrix4f[] poses) {
        Mesh.RenderProperties props = mesh.getRenderProperties();
        ResourceLocation texture = props == null ? null : props.customTexturePath();
        if (texture == null) {
            return;
        }
        mesh.initialize();
        mesh.draw(poseStack, buffers, RenderType.armorCutoutNoCull(texture), light, 1.0F, 1.0F, 1.0F, 1.0F, OverlayTexture.NO_OVERLAY, armature, poses);
    }
}
