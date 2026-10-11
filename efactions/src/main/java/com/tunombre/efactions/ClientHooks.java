package com.tunombre.efactions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Solo cliente. Un jugador esposado/atado no puede agacharse: la pose de agachado de Epic Fight
 * (piernas abiertas, cuerpo adelantado) no coincide con el modelo vanilla sobre el que Cuffed dibuja
 * las esposas, y las esposas salian flotando. Sin agacharse, ambas poses coinciden.
 */
public class ClientHooks {
    private static final Logger LOG = LoggerFactory.getLogger("efactions");
    private boolean init;
    private Method mCap, mArms, mLegs, mHead;

    private void init() {
        init = true;
        try {
            Class<?> caps = Class.forName("com.lazrproductions.cuffed.api.CuffedAPI$Capabilities");
            Class<?> iface = Class.forName("com.lazrproductions.cuffed.cap.base.IRestrainableCapability");
            mCap = caps.getMethod("getRestrainableCapability", Player.class);
            mArms = iface.getMethod("armsRestrained");
            mLegs = iface.getMethod("legsRestrained");
            mHead = iface.getMethod("headRestrained");
        } catch (Throwable t) {
            mCap = null;
            LOG.info("Cuffed no encontrado en el cliente: sin ajuste de agachado ({})", t.toString());
        }
    }

    private boolean restrained(Player p) {
        if (!init) init();
        if (mCap == null) return false;
        try {
            Object cap = mCap.invoke(null, p);
            if (cap == null) return false;
            return (boolean) mArms.invoke(cap) || (boolean) mLegs.invoke(cap) || (boolean) mHead.invoke(cap);
        } catch (Throwable t) {
            mCap = null;
            return false;
        }
    }

    @SubscribeEvent
    public void onInput(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer lp) || lp != Minecraft.getInstance().player) return;
        if (e.getInput().shiftKeyDown && restrained(lp)) e.getInput().shiftKeyDown = false;
    }
}
