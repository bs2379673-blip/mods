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
    private static net.minecraft.client.model.geom.ModelPart part(PlayerModel m, String name, net.minecraft.client.model.geom.ModelPart def) {
        try {
            net.minecraft.client.model.geom.ModelPart p = m.root().getChild(name);
            return p != null ? p : def;
        } catch (Exception e) {
            return def;
        }
    }

    public HairLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState s, float yRot, float xRot) {
        if (s.isInvisible) {
            return;
        }
        HairRender.Frame f = ((FabricRenderState) (Object) s).getData(HairRender.KEY);
        // las partes "de verdad" del modelo (si EMF o un paquete de animaciones las reemplaza, se usan las suyas,
        // asi el pelo sigue a la cabeza al nadar, volar, etc.)
        PlayerModel m = getParentModel();
        HairRender.submitFollowing(f, ps, collector, light, m.root(), part(m, "head", m.head), part(m, "body", m.body));
    }
}
