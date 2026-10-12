package com.tunombre.efzombies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.mob.ZombiePatch;

/**
 * Solo cliente. Cambia las animaciones de movimiento (reposo, caminar, perseguir) de los zombis de Epic Fight
 * por variantes propias. La variante sale del UUID del zombi (cada zombi camina distinto, y todos los clientes
 * eligen la misma). Si el zombi lleva algo en la mano principal usa la version "rigida" (sufijo h).
 */
public class ZombieClient {
    private int tick;

    private static int pick(Entity e, int salt, int n) {
        long h = e.getUUID().getMostSignificantBits() * 0x9E3779B97F4A7C15L + e.getUUID().getLeastSignificantBits() + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (int) Math.floorMod(h, (long) n);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if ((++tick & 3) != 0) return;                        // cada 4 ticks basta
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Entity ent : level.entitiesForRendering()) {
            if (!(ent instanceof Zombie z) || !z.isAlive() || z instanceof Trepador) continue;
            LivingEntityPatch<?> lp = EpicFightCapabilities.getEntityPatch(z, LivingEntityPatch.class);
            if (!(lp instanceof ZombiePatch)) continue;
            ClientAnimator an = lp.getClientAnimator();
            if (an == null) continue;
            boolean item = !z.getMainHandItem().isEmpty();
            AnimationManager.AnimationAccessor<? extends StaticAnimation> walk, chase, idle;
            if (!item && pick(z, 9, 100) < 18) {            // ~18% de los zombis van a cuatro patas (si no llevan nada en la mano)
                walk = EfZombies.ZQW;
                chase = EfZombies.ZQC;
                idle = EfZombies.ZQI;
            } else {
                walk = (item ? EfZombies.ZWH : EfZombies.ZW)[pick(z, 1, EfZombies.ZW.length)];
                chase = (item ? EfZombies.ZCH : EfZombies.ZC)[pick(z, 2, EfZombies.ZC.length)];
                idle = (item ? EfZombies.ZIH : EfZombies.ZI)[pick(z, 3, EfZombies.ZI.length)];
            }
            // Epic Fight puede reponer las suyas (cambio de objeto en la mano): se comprueba y se reimponen
            if (an.getLivingMotion(LivingMotions.WALK) != walk) an.addLivingAnimation(LivingMotions.WALK, walk);
            if (an.getLivingMotion(LivingMotions.CHASE) != chase) an.addLivingAnimation(LivingMotions.CHASE, chase);
            if (an.getLivingMotion(LivingMotions.IDLE) != idle) an.addLivingAnimation(LivingMotions.IDLE, idle);
        }
    }
}
