package com.annis.vfx.compat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public interface HudRenderCallback {
    void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter);

    Event<HudRenderCallback> EVENT = new Event<>(() -> NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
        for (HudRenderCallback c : HudRenderCallback.EVENT.listeners()) c.onHudRender(e.getGuiGraphics(), e.getPartialTick());
    }));
}
