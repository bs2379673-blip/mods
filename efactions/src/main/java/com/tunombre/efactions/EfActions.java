package com.tunombre.efactions;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

/**
 * Registra las animaciones de acciones en Epic Fight (ids efactions:biped/actions/<nombre>)
 * y engancha su reproduccion en el servidor (ver ServerHooks).
 * Debe estar instalado en el servidor y en todos los clientes.
 */
@Mod(EfActions.MODID)
public class EfActions {
    public static final String MODID = "efactions";

    public static AnimationManager.AnimationAccessor<StaticAnimation> INJECT, BANDAGE, MEDKIT, SPRAY,
            GRENADE_PULL, GRENADE_HOLD, GRENADE_THROW, LOOT, CUFFED_FRONT, CUFFED_BACK, RESTRAIN, TRAPPED, PILLORY, FLASH_R, FLASH_L, LOCKPICK, ARMOR_PLATE,
            PRONE_RIFLE_IDLE, PRONE_RIFLE_MOVE, PRONE_PISTOL_IDLE, PRONE_PISTOL_MOVE, NAIL_HOLD, WALKIE_R, WALKIE_L,
            NAIL_SHOOT, NAIL_RELOAD, BOW_HOLD, BOW_DRAW, BOW_SHOOT, XBOW_HOLD, XBOW_SHOOT, XBOW_RELOAD;

    public EfActions() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EfActions::onAnimations);
        MinecraftForge.EVENT_BUS.register(new ServerHooks());
        MinecraftForge.EVENT_BUS.register(new ShieldCompat());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ClientHooks()));
    }

    private static void onAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(MODID, EfActions::build);
    }

    private static void build(AnimationManager.AnimationBuilder b) {
        INJECT = reg(b, "inject", false);
        BANDAGE = reg(b, "bandage", false);
        MEDKIT = reg(b, "medkit", false);
        SPRAY = reg(b, "spray", false);
        GRENADE_PULL = reg(b, "grenade_pull", false);
        GRENADE_HOLD = reg(b, "grenade_hold", true);
        GRENADE_THROW = reg(b, "grenade_throw", false);
        LOOT = reg(b, "loot", true);
        CUFFED_FRONT = reg(b, "cuffed_front", true);
        CUFFED_BACK = reg(b, "cuffed_back", true);
        RESTRAIN = reg(b, "restrain", false);
        TRAPPED = reg(b, "trapped", true);
        PILLORY = reg(b, "pillory", true);
        FLASH_R = reg(b, "flashlight_r", true);
        FLASH_L = reg(b, "flashlight_l", true);
        LOCKPICK = reg(b, "lockpick", true);
        ARMOR_PLATE = reg(b, "armor_plate", false);
        PRONE_RIFLE_IDLE = reg(b, "prone_rifle_idle", true);
        PRONE_RIFLE_MOVE = reg(b, "prone_rifle_move", true);
        PRONE_PISTOL_IDLE = reg(b, "prone_pistol_idle", true);
        PRONE_PISTOL_MOVE = reg(b, "prone_pistol_move", true);
        NAIL_HOLD = reg(b, "nail_hold", true);
        WALKIE_R = reg(b, "walkie_r", true);
        WALKIE_L = reg(b, "walkie_l", true);
        NAIL_SHOOT = reg(b, "nail_shoot", false);
        NAIL_RELOAD = reg(b, "nail_reload", false);
        BOW_HOLD = reg(b, "bow_hold", true);
        BOW_DRAW = reg(b, "bow_draw", false);
        BOW_SHOOT = reg(b, "bow_shoot", false);
        XBOW_HOLD = reg(b, "xbow_hold", true);
        XBOW_SHOOT = reg(b, "xbow_shoot", false);
        XBOW_RELOAD = reg(b, "xbow_reload", false);
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> reg(AnimationManager.AnimationBuilder b, String name, boolean repeat) {
        return b.nextAccessor("biped/actions/" + name, acc -> new StaticAnimation(0.12F, repeat, acc, Armatures.BIPED));
    }
}
