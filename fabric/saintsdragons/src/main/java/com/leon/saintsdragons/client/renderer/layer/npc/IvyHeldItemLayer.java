package com.leon.saintsdragons.client.renderer.layer.npc;

import com.leon.saintsdragons.client.compat.geo.SDRenderState;
import com.leon.saintsdragons.server.entity.npc.IvyTheDragonMerchant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;

import java.util.List;

/** Port 1.21.11 / GeckoLib 5: lo que Ivy tiene en la mano derecha. */
public class IvyHeldItemLayer extends BlockAndItemGeoLayer<IvyTheDragonMerchant, Void, SDRenderState> {
    private static final String RIGHT_HAND_LOCATOR = "rightArmItemLocator";
    private static final DataTicket<ItemStack> HELD = DataTicket.create("saintsdragons_ivy_held", ItemStack.class);

    public IvyHeldItemLayer(com.leon.saintsdragons.client.compat.geo.LegacyGeoEntityRenderer<IvyTheDragonMerchant> renderer) {
        super(renderer);
    }

    @Override
    public void addRenderData(IvyTheDragonMerchant animatable, Void relatedObject, SDRenderState state, float partialTick) {
        ItemStack stack = animatable.getRecoveryItemForRender();
        if (stack.isEmpty()) {
            stack = animatable.getSwordForRender();
        }
        state.addGeckolibData(HELD, stack.copy());
    }

    @Override
    protected List<RenderData<SDRenderState>> getRelevantBones(SDRenderState state, BakedGeoModel model) {
        ItemStack held = state.getOrDefaultGeckolibData(HELD, ItemStack.EMPTY);
        if (held.isEmpty()) {
            return List.of();
        }
        return List.of(new RenderData<>(RIGHT_HAND_LOCATOR, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                (bone, s) -> Either.left(s.getOrDefaultGeckolibData(HELD, ItemStack.EMPTY))));
    }

    @Override
    protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStack stack, ItemDisplayContext displayContext,
                                         SDRenderState state, SubmitNodeCollector collector, CameraRenderState camera,
                                         int packedLight, int packedOverlay, int color) {
        poseStack.translate(0.0D, -0.0625D, -0.1D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        super.submitItemStackRender(poseStack, bone, stack, displayContext, state, collector, camera, packedLight, packedOverlay, color);
    }
}
