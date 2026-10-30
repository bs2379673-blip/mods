package com.annis.vfx.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;

public final class ServerLivingEntityEvents {
    private ServerLivingEntityEvents() {
    }

    public interface AllowDeath {
        boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
    }

    public interface AfterDeath {
        void afterDeath(LivingEntity entity, DamageSource source);
    }

    /** false = no muere (en Forge: se cancela la muerte; quien lo cancela debe dejarle algo de vida). */
    public static final Event<AllowDeath> ALLOW_DEATH = new Event<>(() -> NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false,
            LivingDeathEvent.class, e -> {
                if (e.getEntity().level().isClientSide) {
                    return;
                }
                for (AllowDeath a : ServerLivingEntityEvents.ALLOW_DEATH.listeners()) {
                    if (!a.allowDeath(e.getEntity(), e.getSource(), 0)) {
                        e.setCanceled(true);
                        if (e.getEntity().getHealth() <= 0) {
                            e.getEntity().setHealth(1.0f);
                        }
                        return;
                    }
                }
            }));

    public static final Event<AfterDeath> AFTER_DEATH = new Event<>(() -> NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
            LivingDeathEvent.class, e -> {
                if (e.getEntity().level().isClientSide) {
                    return;
                }
                for (AfterDeath a : ServerLivingEntityEvents.AFTER_DEATH.listeners()) {
                    a.afterDeath(e.getEntity(), e.getSource());
                }
            }));
}
