package com.tunombre.obscurearcade;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;


/** Animaciones de Epic Fight del duelo: golpes y reacciones de Epic Fight mas posturas propias (pelea, burla, victoria, derrota). */
public final class Anims {
    public static AnimationManager.AnimationAccessor<StaticAnimation> STANCE, TAUNT, WIN, SLUMP;

    private Anims() { }

    static void register(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(ObscureArcade.MODID, b -> {
            STANCE = b.nextAccessor("biped/arcade/stance", acc -> new StaticAnimation(0.2F, true, acc, Armatures.BIPED));
            TAUNT = b.nextAccessor("biped/arcade/taunt", acc -> new StaticAnimation(0.15F, false, acc, Armatures.BIPED));
            WIN = b.nextAccessor("biped/arcade/win", acc -> new StaticAnimation(0.2F, false, acc, Armatures.BIPED));
            SLUMP = b.nextAccessor("biped/arcade/slump", acc -> new StaticAnimation(0.2F, false, acc, Armatures.BIPED));
            Styles.registerMine("stance", STANCE);
            Styles.registerMine("taunt", TAUNT);
            Styles.registerMine("win", WIN);
            Styles.registerMine("slump", SLUMP);
        });
    }

    public static void play(ServerPlayer p, AnimationManager.AnimationAccessor<? extends StaticAnimation> acc) {
        if (acc == null) return;
        try {
            ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
            if (pp != null) pp.playAnimationSynchronized(acc, 0.04F);
        } catch (RuntimeException ignored) { }
    }

    public static void play(ServerPlayer p, String name) { play(p, Styles.find(name)); }
}
