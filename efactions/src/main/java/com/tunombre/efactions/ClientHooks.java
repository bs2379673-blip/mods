package com.tunombre.efactions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import yesman.epicfight.config.ClientConfig;
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

    private int tick;
    private boolean logged;

    /**
     * Ajustes de Epic Fight pensados para un servidor, reaplicados cada segundo (el jugador puede tocarlos en su menu):
     * - Puños: con la mano vacia Epic Fight cancelaba el picado de bloques. Se fuerza el modo "Adaptive" y la mano vacia fuera de la
     *   lista de combate: con un bloque en la mira se pica, y con el aire o un enemigo se pega.
     * - Camara: las "acciones en primera persona" de Epic Fight giran la camara sola al mirar casi vertical (arriba o abajo).
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++tick % 20 != 0) return;
        try {
            ClientConfig.enablePovAction = false;
            ClientConfig.preferenceWork = ClientConfig.PreferenceWork.ADAPTIVE;
            if (ClientConfig.combatPreferredItems != null) ClientConfig.combatPreferredItems.remove(Items.AIR);
            if (!logged) { logged = true; LOG.info("efactions: ajustes de Epic Fight aplicados (puños pican, camara sin giros)"); }
        } catch (Throwable t) {
            if (!logged) { logged = true; LOG.warn("efactions: no se pudieron aplicar los ajustes de Epic Fight", t); }
        }
    }
}
