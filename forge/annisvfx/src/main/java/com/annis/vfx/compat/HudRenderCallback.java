package com.annis.vfx.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;

public interface HudRenderCallback {
    void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter);

    Event<HudRenderCallback> EVENT = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
        for (HudRenderCallback c : HudRenderCallback.EVENT.listeners()) c.onHudRender(e.getGuiGraphics(), DeltaTracker.INSTANCE);
    }));
}
