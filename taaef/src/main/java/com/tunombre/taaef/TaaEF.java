package com.tunombre.taaef;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;

/**
 * Compatibilidad de "Tacz Animation Additions" (locknar_doorkick) con Epic Fight / Epic Arsenal.
 * TAA dibuja sus poses de tercera persona sobre el modelo de vainilla, que con Epic Fight no se usa. Aqui se lee el
 * estado que TAA ya sincroniza para cada jugador y se reproduce la animacion de Epic Fight equivalente en una capa
 * compuesta, encima de las poses de arma de Epic Arsenal.
 */
@Mod(TaaEF.MODID)
public class TaaEF {
    public static final String MODID = "taaef";
    public static final Logger LOG = LogUtils.getLogger();

    public TaaEF() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener((AnimationManager.AnimationRegistryEvent e) -> e.newBuilder(MODID, Anims::build));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.addListener(ClientDriver::onTick));
    }
}
