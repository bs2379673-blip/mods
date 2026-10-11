package com.tunombre.saclothing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Envuelve el renderer de Curios original de una prenda de SA (GeckoLib, caja rigida). Si Epic Fight ya la esta
 * dibujando con su malla (CurioMeshLayer.active == esa entidad) no hace nada; en cualquier otro caso
 * (render vanilla, GUI sin Epic Fight, etc.) delega en el renderer original.
 */
public class MeshAwareCurioRenderer implements ICurioRenderer {
    private final ICurioRenderer original;

    MeshAwareCurioRenderer(ICurioRenderer original) {
        this.original = original;
    }

    ICurioRenderer original() {
        return original;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                                                                          RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffers,
                                                                          int light, float limbSwing, float limbSwingAmount, float partialTicks,
                                                                          float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        if (entity != null && entity == CurioMeshLayer.active && ArmorMeshes.get(stack.getItem()) != null) {
            return;
        }
        original.render(stack, slotContext, poseStack, renderLayerParent, buffers, light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }
}
