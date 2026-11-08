package com.leon.saintsdragons.util.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Port 1.21.11: el ImageButton de 1.20.1 (textura con u, v y corrimiento al pasar el mouse). */
public class TexButton extends Button {
    private final Identifier texture;
    private final int u, v, hoverDy, texW, texH;

    public TexButton(int x, int y, int w, int h, int u, int v, int hoverDy, Identifier texture, int texW, int texH, OnPress onPress,
                     Component message) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.hoverDy = hoverDy;
        this.texW = texW;
        this.texH = texH;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int vv = this.v + (this.isHoveredOrFocused() ? this.hoverDy : 0);
        Gui.blit(g, this.texture, getX(), getY(), (float) this.u, (float) vv, this.width, this.height, this.texW, this.texH);
    }
}
