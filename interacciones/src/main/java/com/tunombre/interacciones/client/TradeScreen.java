package com.tunombre.interacciones.client;

import com.tunombre.interacciones.TradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** pantalla del tradeo (dibujada a mano, sin textura) */
public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private Button ready;

    public TradeScreen(TradeMenu m, Inventory inv, Component title) {
        super(m, inv, title);
        imageWidth = 176;
        imageHeight = 182;
        inventoryLabelY = TradeMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        ready = addRenderableWidget(Button.builder(Component.literal("LISTO"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 62, topPos + 82 - 2, 52, 14).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF58C8FF);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0141418);
        g.fill(x + 88, y + 14, x + 89, y + 84, 0x6058C8FF);
        for (Slot s : menu.slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF2A2A32);
        if (menu.imReady()) g.fill(x + TradeMenu.MINE_X + 17, y + TradeMenu.TOP - 2, x + TradeMenu.MINE_X + 71, y + TradeMenu.TOP + 54, 0x3044FF66);
        if (menu.otherReady()) g.fill(x + TradeMenu.THEIRS_X - 1, y + TradeMenu.TOP - 2, x + TradeMenu.THEIRS_X + 53, y + TradeMenu.TOP + 54, 0x3044FF66);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Tu oferta", TradeMenu.MINE_X + 18, 14, 0xFFB8E4FF, false);
        g.drawString(font, menu.otherName, TradeMenu.THEIRS_X, 14, 0xFFB8E4FF, false);
        g.drawString(font, menu.imReady() ? "§aLISTO" : "§7...", TradeMenu.MINE_X + 18, 84, 0xFFFFFFFF, false);
        g.drawString(font, menu.otherReady() ? "§aLISTO" : "§7esperando", TradeMenu.THEIRS_X, 84, 0xFFFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF9A9AA6, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        if (ready != null) ready.setMessage(Component.literal(menu.imReady() ? "QUITAR" : "LISTO"));
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
