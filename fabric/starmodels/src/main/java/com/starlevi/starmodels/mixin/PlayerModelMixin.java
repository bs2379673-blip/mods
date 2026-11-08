package com.starlevi.starmodels.mixin;

import com.starlevi.starmodels.client.AvatarLayer;
import com.starlevi.starmodels.client.HidesVanilla;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Con personaje, el jugador de Minecraft no se dibuja (las partes quedan posadas, para que el personaje
 * las siga, pero invisibles). Minecraft vuelve a posar el modelo justo antes de dibujarlo, asi que esto
 * vale para cada jugador por separado.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin implements HidesVanilla {
    @Unique
    private boolean starmodels$hid;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void starmodels$hide(AvatarRenderState s, CallbackInfo ci) {
        AvatarLayer.Frame f = AvatarLayer.frame(s);
        if (f != null && f.ready() != null) {
            starmodels$set(false);
            this.starmodels$hid = true;
        } else if (this.starmodels$hid) {
            starmodels$show();
            PlayerModel m = (PlayerModel) (Object) this;
            m.hat.visible = s.showHat;
            m.jacket.visible = s.showJacket;
            m.leftPants.visible = s.showLeftPants;
            m.rightPants.visible = s.showRightPants;
            m.leftSleeve.visible = s.showLeftSleeve;
            m.rightSleeve.visible = s.showRightSleeve;
        }
    }

    @Unique
    private void starmodels$set(boolean v) {
        PlayerModel m = (PlayerModel) (Object) this;
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg,
                m.rightPants, m.leftPants}) {
            p.visible = v;
        }
        for (String n : new String[]{"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            try {
                ModelPart p = m.root().getChild(n);
                if (p != null) {
                    p.visible = v;
                }
            } catch (Exception ignored) {
                // no esta
            }
        }
    }

    @Override
    public void starmodels$show() {
        if (this.starmodels$hid) {
            this.starmodels$hid = false;
            starmodels$set(true);
        }
    }
}
