package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.cosmeticos.geo.Cosm;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** capa para el render normal del jugador: los huesos son las partes del modelo vanilla (cabeza, cuerpo) */
public class CosLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CosLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float pt, float age, float yaw, float pitch) {
        long mask = CosClient.mask(p.getUUID());
        if (mask == 0L || p.isInvisible() || p.isSpectator()) return;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        CosRender.draw(p, mask, pt, light, buf, ps, (stack, bone) -> {
            stack.pushPose();
            switch (bone) {
                case HEAD -> m.head.translateAndRotate(stack);
                case CHEST, PELVIS -> m.body.translateAndRotate(stack);
                case HAND_R -> m.rightArm.translateAndRotate(stack);
                case ROOT -> { }
            }
            // de las coordenadas del modelo (y hacia abajo) a las nuestras: x izquierda, y arriba, z delante
            stack.scale(1F, -1F, -1F);
            if (bone == Cosm.Bone.PELVIS || bone == Cosm.Bone.ROOT) stack.translate(0F, -0.75F, 0F);
            if (bone == Cosm.Bone.HAND_R) stack.translate(0F, -0.62F, 0F);   // del hombro a la palma
            return true;
        });
    }
}
