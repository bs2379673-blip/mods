package com.starlevi.starscenes.mixin;

import com.starlevi.starscenes.client.Cinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La camara de cine de las escenas: despues de que Minecraft acomoda la camara, la ponemos donde va. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void starscenes$cinematic(Level level, Entity entity, boolean detachedIn, boolean mirrored, float partial, CallbackInfo ci) {
        Cinematic.Pose p = Cinematic.camera(partial);
        if (p != null) {
            this.setRotation(p.yaw(), p.pitch());
            this.setPosition(p.pos());
            this.detached = true;
        }
    }
}
