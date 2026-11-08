package com.leon.saintsdragons.util.animation;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Port 1.21.11 (GeckoLib 5): en GeckoLib 4 esto mantenia la reproduccion andando en los ticks del cliente
 * sin evaluar el esqueleto. GeckoLib 5 calcula las animaciones por tiempo al dibujar, asi que alcanza con
 * el controlador normal.
 */
public class EntityAnimationController<T extends LivingEntity & TickingGeoEntity> extends AnimationController<T> {
    public EntityAnimationController(T entity, String name, int transitionTicks, AnimationStateHandler<T> predicate) {
        super(name, transitionTicks, predicate);
    }

    /** Ya no hace falta (ver arriba). */
    public final void tickPlayback(Object model, Object state, Object resource) {
    }
}
