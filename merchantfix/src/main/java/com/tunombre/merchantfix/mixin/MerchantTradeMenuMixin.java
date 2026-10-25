package com.tunombre.merchantfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * En 1.20.1 el paquete "clic en boton de menu" escribe el numero de boton como 1 byte. El menu de comprar/vender del
 * mercader de Survivors Arsenal usa botones 1000 + (cantidad-1)*1000 + indice_de_oferta, asi que al servidor llega solo el
 * byte bajo (negativo o pequeno) y el menu lo descarta en silencio.
 * Aqui se reconstruye el numero original: se busca la combinacion (cantidad, oferta) mas pequena cuyo byte bajo coincide.
 * Si el numero ya llega completo (>= 1000) no se toca.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_combat.screen.MerchantTradeMenu", remap = false)
public abstract class MerchantTradeMenuMixin {
    @ModifyVariable(method = {"m_6366_", "clickMenuButton"}, at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private int merchantfix$restore(int id) {
        if (id >= 1000 || id == 0) return id;
        int b = id & 0xFF;
        if (b == 0) return 0;
        int n = 0;
        try {
            Method m = this.getClass().getMethod("getTrades");
            n = ((List<?>) m.invoke(this)).size();
        } catch (Throwable t) {
            return id;
        }
        n = Math.min(n, 999);
        for (int q = 1; q <= 64; q++) {
            for (int idx = 0; idx < n; idx++) {
                if (((1000 * q + idx) & 0xFF) == b) return 1000 + (q - 1) * 1000 + idx;
            }
        }
        return id;
    }
}
