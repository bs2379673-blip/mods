package com.starlevi.starbody.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Para encontrar la cabeza de verdad dentro del modelo (aunque EMF o un paquete la reemplace). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> starbody$children();

    @Accessor("cubes")
    List<ModelPart.Cube> starbody$cubes();
}
