package com.starlevi.starpackport.mixin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.starlevi.starpackport.ModelRotations;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Acepta cualquier angulo en los cubos (1.20.1 solo dejaba -45, -22.5, 0, 22.5 y 45) y giros en varios ejes. */
@Mixin(BlockElement.Deserializer.class)
public abstract class BlockElementDeserializerMixin {
    @Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
    private void starpackport$anyAngle(JsonObject json, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GsonHelper.getAsFloat(json, "angle"));
    }

    @Inject(method = "getRotation", at = @At("RETURN"))
    private void starpackport$xyz(JsonObject json, CallbackInfoReturnable<BlockElementRotation> cir) {
        BlockElementRotation rot = cir.getReturnValue();
        if (rot == null || !json.has("rotation") || !json.get("rotation").isJsonObject()) {
            return;
        }
        JsonObject r = json.getAsJsonObject("rotation");
        if (r.has("starpackport_xyz") && r.get("starpackport_xyz").isJsonArray()) {
            JsonArray a = r.getAsJsonArray("starpackport_xyz");
            if (a.size() == 3) {
                ModelRotations.registerXyz(rot, a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            }
        }
    }
}
