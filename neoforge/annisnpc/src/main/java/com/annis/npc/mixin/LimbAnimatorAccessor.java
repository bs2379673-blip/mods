package com.annis.npc.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface LimbAnimatorAccessor {
    @Accessor("speedOld")
    float annisnpc$getPrevSpeed();

    @Accessor("speedOld")
    void annisnpc$setPrevSpeed(float v);

    @Accessor("speed")
    float annisnpc$getSpeed();

    @Accessor("speed")
    void annisnpc$setSpeed(float v);

    @Accessor("position")
    float annisnpc$getPos();

    @Accessor("position")
    void annisnpc$setPos(float v);
}
