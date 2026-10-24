package com.tunombre.cruorfight;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Addon solo de cliente: hace que las heridas de Cruor se vean sobre los cuerpos de Epic Fight. */
@Mod(CruorFight.MODID)
public class CruorFight {
    public static final String MODID = "cruorfight";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public CruorFight() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHook::init);
    }
}
