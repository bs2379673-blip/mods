package com.tunombre.obscurearcade.mixin;

import com.cotii.cubosu.client.qte.ManiaSession;
import com.tunombre.obscurearcade.client.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cada vez que CubOsu juzga una nota (carril + resultado) se avisa al duelo; el servidor decide el golpe. */
@Mixin(value = ManiaSession.class, remap = false)
public abstract class ManiaSessionMixin {
    @Shadow public boolean remote;

    @Inject(method = "judge(III)V", at = @At("HEAD"), remap = false, require = 0)
    private void obscurearcade$judge(int note, int lane, int judgement, CallbackInfo ci) {
        if (!this.remote) ClientState.onJudge(lane, judgement);
    }
}
