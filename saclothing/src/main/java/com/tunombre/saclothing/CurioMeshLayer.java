package com.tunombre.saclothing;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Sustituye a la capa de Curios de Epic Fight: primero dibuja con malla de Epic Fight las prendas de SA que haya en
 * slots de Curios (siguen el esqueleto hueso por hueso) y despues deja que la capa original (la de Epic Fight o la de
 * sa_epicfight_compat) dibuje el resto (mochilas, etc.). Las prendas con malla se saltan en el dibujo original
 * (ver {@link MeshAwareCurioRenderer}).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CurioMeshLayer extends PatchedLayer {
    /** Entidad que estamos dibujando ahora mismo con Epic Fight (para que el dibujo original omita las prendas ya dibujadas). */
    static volatile LivingEntity active;

    private final PatchedLayer delegate;

    CurioMeshLayer(PatchedLayer delegate) {
        this.delegate = delegate;
    }

    PatchedLayer delegate() {
        return delegate;
    }

    @Override
    public void renderLayer(LivingEntity entity, LivingEntityPatch patch, RenderLayer vanillaLayer, PoseStack poseStack,
                            MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        LivingEntity previous = active;
        active = entity;
        try {
            try {
                drawMeshes(entity, patch, poseStack, buffers, light, poses);
            } catch (Throwable t) {
                if (!failedOnce) {
                    failedOnce = true;
                    t.printStackTrace();
                }
            }
            if (delegate != null) {
                delegate.renderLayer(entity, patch, vanillaLayer, poseStack, buffers, light, poses, bob, yRot, xRot, partialTicks);
            }
        } finally {
            active = previous;
        }
    }

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity entity, RenderLayer vanillaLayer, PoseStack poseStack,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        renderLayer(entity, patch, vanillaLayer, poseStack, buffers, light, poses, bob, yRot, xRot, partialTicks);
    }

    private static boolean failedOnce;

    private static void drawMeshes(LivingEntity entity, LivingEntityPatch patch, PoseStack poseStack, MultiBufferSource buffers,
                                   int light, OpenMatrix4f[] poses) {
        List<ItemStack> worn = new ArrayList<>(4);
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> handler.getCurios().forEach((id, stacksHandler) -> {
            ICurioStacksHandler h = stacksHandler;
            IDynamicStackHandler stacks = h.getStacks();
            IDynamicStackHandler cosmetic = h.getCosmeticStacks();
            NonNullList<Boolean> renders = h.getRenders();
            for (int i = 0; i < stacks.getSlots(); ++i) {
                ItemStack stack = cosmetic.getStackInSlot(i);
                boolean renderable = renders.size() > i && renders.get(i);
                if (stack.isEmpty() && renderable) {
                    stack = stacks.getStackInSlot(i);
                }
                if (!stack.isEmpty()) {
                    worn.add(stack);
                }
            }
        }));
        for (ItemStack stack : worn) {
            if (!(stack.getItem() instanceof ArmorItem armor)) {
                continue;
            }
            SkinnedMesh mesh = ArmorMeshes.get(armor);
            if (mesh == null) {
                continue;
            }
            // si lleva la misma prenda en el slot de armadura, la dibuja la capa de armadura de Epic Fight
            if (entity.getItemBySlot(armor.getEquipmentSlot()).getItem() == armor) {
                continue;
            }
            ArmorMeshes.draw(mesh, patch.getArmature(), poseStack, buffers, light, poses);
        }
    }
}
