package com.starlevi.starbody.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** La capa del jugador que dibuja su pelo con fisicas. */
public class HairLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public HairLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState s, float yRot, float xRot) {
        if (s.isInvisible) {
            return;
        }
        HairRender.Frame f = ((FabricRenderState) (Object) s).getData(HairRender.KEY);
        HairRender.submit(f, ps, collector, light, getParentModel().head, getParentModel().body);
    }
}
