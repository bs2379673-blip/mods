package com.tunombre.flashlightef;

import net.minecraftforge.fml.common.Mod;

/**
 * Compatibilidad de Flashier Flashlights con Epic Fight. Toda la logica esta en el mixin {@code AimMixin}: con Epic Fight
 * el jugador no usa el modelo de vainilla, asi que la pose "brazo en alto" de la linterna no existe y la orientacion del
 * item en la mano (de donde el mod saca la direccion de la luz) queda al reves. Para los jugadores en modo Epic Fight
 * la luz apunta hacia donde mira el jugador.
 */
@Mod(FlashlightEF.MODID)
public class FlashlightEF {
    public static final String MODID = "flashlightef";
}
