package com.tunombre.tacui;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

/** Tactical UI: HUD e inventario tacticos. Solo cliente (un servidor sin el mod acepta a los clientes que lo tengan). */
@Mod(TacUI.ID)
public class TacUI {
    public static final String ID = "tacui";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public TacUI() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (s, b) -> true));
    }
}
