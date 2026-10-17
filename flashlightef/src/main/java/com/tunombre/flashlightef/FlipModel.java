package com.tunombre.flashlightef;

import com.mojang.blaze3d.vertex.PoseStack;
import com.keerdm.flashlightmod.client.render.GlassPositionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Modelo de la linterna con la orientacion corregida para Epic Fight. El modelo de la linterna trae una rotacion de
 * "tercera persona" pensada para el brazo en alto de vainilla; con el brazo de Epic Fight el lente queda mirando hacia
 * atras. Para jugadores en modo Epic Fight se da vuelta 180 grados alrededor del agarre.
 */
public class FlipModel extends BakedModelWrapper<BakedModel> {
    /** pivote: el agarre de la linterna, en coordenadas del modelo centradas en el origen (bloques) */
    private static final float PY = -0.29f;
    /** la misma transformacion como matriz (es su propia inversa) */
    public static final Matrix4f FLIP = new Matrix4f().translate(0, PY, 0).rotateX((float) Math.PI).translate(0, -PY, 0);

    private final ItemOverrides overrides;

    public FlipModel(BakedModel original) {
        super(original);
        ItemOverrides inner = original.getOverrides();
        this.overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                BakedModel r = inner.resolve(model, stack, level, entity, seed);
                if (r == null || r instanceof FlipModel) return r;
                return new FlipModel(r);
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() { return overrides; }

    @Override
    public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean left) {
        originalModel.applyTransform(ctx, pose, left);
        if ((ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) && epicPlayerRendering()) {
            pose.translate(0, PY, 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
            pose.translate(0, -PY, 0);
        }
        return this;
    }

    private static boolean epicPlayerRendering() {
        try {
            UUID u = GlassPositionManager.getCurrentRenderingPlayerUUID();
            Minecraft mc = Minecraft.getInstance();
            if (u == null || mc.level == null) return false;
            Player p = mc.level.getPlayerByUUID(u);
            return p != null && EpicSupport.isEpicMode(p);
        } catch (Throwable t) { return false; }
    }
}
