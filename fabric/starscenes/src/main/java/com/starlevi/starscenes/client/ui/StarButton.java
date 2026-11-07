package com.starlevi.starscenes.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Boton con el estilo de StarScenes (normal, de color, peligroso, fantasma o pestaña elegida). */
public class StarButton extends AbstractButton {
    public enum Style {
        NORMAL, ACCENT, DANGER, GHOST, TAB, TAB_ON, CHOICE
    }

    private final Runnable action;
    private Style style;
    private ItemStack icon = ItemStack.EMPTY;
    private boolean left;
    private String hint;

    public StarButton(int x, int y, int w, int h, String text, Style style, Runnable action) {
        super(x, y, w, h, Component.literal(text));
        this.action = action;
        this.style = style;
    }

    public StarButton icon(ItemStack st) {
        this.icon = st;
        return this;
    }

    /** Texto alineado a la izquierda (para listas). */
    public StarButton left() {
        this.left = true;
        return this;
    }

    /** Un texto chico a la derecha (ej. una tecla o un numero). */
    public StarButton hint(String h) {
        this.hint = h;
        return this;
    }

    public StarButton style(Style s) {
        this.style = s;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.run();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
        Font f = Minecraft.getInstance().font;
        boolean hot = this.active && this.isHoveredOrFocused();
        int fill, border, text;
        switch (this.style) {
            case ACCENT -> {
                fill = hot ? 0xFF9B66E0 : 0xFF7E4FC0;
                border = hot ? 0xFFE2C4FF : 0xFFB98CF5;
                text = 0xFFFFFFFF;
            }
            case DANGER -> {
                fill = hot ? 0xFF6E2733 : 0xFF4A1D27;
                border = hot ? UI.BAD : 0xFF8C3A48;
                text = hot ? 0xFFFFFFFF : 0xFFFFC2C9;
            }
            case GHOST -> {
                fill = hot ? 0x553D3156 : 0x00000000;
                border = hot ? UI.BORDER_HI : 0x00000000;
                text = hot ? UI.TEXT : UI.MUTED;
            }
            case TAB -> {
                fill = hot ? UI.BG3 : UI.BG2;
                border = hot ? UI.BORDER_HI : UI.BORDER;
                text = hot ? UI.TEXT : UI.MUTED;
            }
            case CHOICE -> {
                fill = hot ? 0xF05A3A8C : 0xE01A1426;
                border = hot ? 0xFFE2C4FF : 0xFF6A5492;
                text = hot ? 0xFFFFFFFF : 0xFFE9E0F5;
            }
            case TAB_ON -> {
                fill = UI.BG3;
                border = UI.ACCENT;
                text = UI.ACCENT;
            }
            default -> {
                fill = hot ? UI.BG3 : UI.BG2;
                border = hot ? UI.ACCENT : UI.BORDER;
                text = hot ? 0xFFFFFFFF : UI.TEXT;
            }
        }
        if (!this.active) {
            fill = 0xC0181420;
            border = 0xFF2A2338;
            text = 0xFF5E5570;
        }
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        UI.box(g, x, y, w, h, fill, border);
        if (this.style == Style.CHOICE && hot) {
            // brillo a los costados al pasar el mouse
            g.fill(x + 2, y + 2, x + 4, y + h - 2, 0xFFFFC66B);
            g.fill(x + w - 4, y + 2, x + w - 2, y + h - 2, 0xFFFFC66B);
        }
        if (this.style == Style.TAB_ON) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, UI.ACCENT);
        }
        int tx = x + 6;
        if (!this.icon.isEmpty()) {
            g.renderItem(this.icon, x + 3, y + (h - 16) / 2);
            tx = x + 22;
        }
        int hintW = this.hint == null ? 0 : f.width(this.hint) + 6;
        String s = this.getMessage().getString();
        int ty = y + (h - 8) / 2;
        if (this.left || !this.icon.isEmpty()) {
            g.drawString(f, UI.fit(f, s, x + w - tx - 4 - hintW), tx, ty, text, false);
        } else {
            String t = UI.fit(f, s, w - 8);
            g.drawString(f, t, x + (w - f.width(t)) / 2, ty, text, false);
        }
        if (this.hint != null) {
            g.drawString(f, this.hint, x + w - hintW, ty, this.active ? UI.GOLD : text, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
