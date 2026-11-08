package com.leon.saintsdragons.fabric.mixin.fabric;

import com.leon.saintsdragons.client.input.DragonPartInteractionTargeting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/** Port 1.21.11: a que apunta el jugador ahora se calcula en LocalPlayer.pick (antes en GameRenderer). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPickMixin {
    @ModifyArg(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"),
            index = 4, require = 0)
    private static Predicate<Entity> saintsdragons$filterInteractionParts(Predicate<Entity> predicate) {
        return DragonPartInteractionTargeting.filter(predicate);
    }
}
