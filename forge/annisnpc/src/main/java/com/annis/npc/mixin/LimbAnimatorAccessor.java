package com.annis.npc.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface LimbAnimatorAccessor {
    @Accessor("prevSpeed")
    float annisnpc$getPrevSpeed();

    @Accessor("prevSpeed")
    void annisnpc$setPrevSpeed(float v);

    @Accessor("speed")
    float annisnpc$getSpeed();

    @Accessor("speed")
    void annisnpc$setSpeed(float v);

    @Accessor("pos")
    float annisnpc$getPos();

    @Accessor("pos")
    void annisnpc$setPos(float v);
}
