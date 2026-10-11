package com.tunombre.saclothing;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * SA Clothing x Epic Fight (solo cliente).
 * Las prendas/armaduras de Survivors Arsenal se dibujan con mallas "skinned" de Epic Fight
 * (assets/sa_combat/animmodels/armor/&lt;item&gt;.json) que siguen el esqueleto hueso por hueso,
 * tanto en el slot de armadura como en los slots de Curios.
 */
@Mod(SaClothing.MODID)
public class SaClothing {
    public static final String MODID = "saclothing";

    public SaClothing() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHooks.register();
        }
    }
}
