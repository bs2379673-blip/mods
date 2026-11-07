package com.starlevi.starscenes.client;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * La tienda: cada fila muestra lo que pide (con cuanto tenes de cada cosa), lo que da y el boton de
 * comprar. Se actualiza sola despues de cada compra.
 */
public class ShopScreen extends Screen {
    private static final int ROW = 26;

    private Net.ShopOpen shop;
    private int scroll;
    private boolean closedByServer;

    private ShopScreen(Net.ShopOpen s) {
        super(Component.literal(s.name()));
        this.shop = s;
    }

    public static void show(Net.ShopOpen s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen ss && ss.shop.id().equals(s.id())) {
            ss.shop = s;
            ss.rebuildWidgets();
        } else {
            if (mc.screen instanceof TalkScreen) {
                TalkScreen.end();
            }
            mc.setScreen(new ShopScreen(s));
        }
    }

    private int panelW() {
        return Math.min(340, this.width - 20);
    }

    private int rows() {
        return Math.max(1, Math.min(this.shop.offers().size(), (this.height - 70) / ROW));
    }

    private int panelH() {
        return 34 + rows() * ROW + 8;
    }

    /** Cuantos tiene el jugador del item (mismo item, sin mirar etiquetas). */
    private static int has(ItemStack want) {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return 0;
        }
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(want.getItem())) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static boolean affordable(Net.OfferView o) {
        for (ItemStack st : o.price()) {
            if (has(st) < st.getCount()) {
                return false;
            }
        }
        return o.left() != 0;
    }

    @Override
    protected void init() {
        int pw = panelW(), ph = panelH(), px = (this.width - pw) / 2, py = (this.height - ph) / 2;
        List<Net.OfferView> offers = this.shop.offers();
        this.scroll = Math.max(0, Math.min(this.scroll, offers.size() - rows()));
        for (int r = 0; r < rows() && r + this.scroll < offers.size(); r++) {
            Net.OfferView o = offers.get(r + this.scroll);
            int y = py + 34 + r * ROW;
            StarButton b = new StarButton(px + pw - 70, y + 3, 60, 18, o.left() == 0 ? "Agotado" : "Comprar", StarButton.Style.ACCENT,
                    () -> ClientPlayNetworking.send(new Net.Buy(this.shop.id(), o.index())));
            b.active = affordable(o);
            addRenderableWidget(b);
        }
        addRenderableWidget(new StarButton(px + pw - 20, py + 7, 14, 14, "✕", StarButton.Style.GHOST, this::onClose));
    }

    @Override
    public void tick() {
        // el inventario cambia (compras): se reacomodan los botones
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.tickCount % 10 == 0) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int pw = panelW(), ph = panelH(), px = (this.width - pw) / 2, py = (this.height - ph) / 2;
        UI.panel(g, px, py, pw, ph);
        g.drawString(this.font, "✦ " + this.shop.name(), px + 10, py + 10, UI.GOLD, false);
        UI.separator(g, px + 8, py + 27, pw - 16);
        List<Net.OfferView> offers = this.shop.offers();
        if (offers.isEmpty()) {
            g.drawCenteredString(this.font, "No hay nada para vos por ahora", this.width / 2, py + 40, UI.MUTED);
        }
        for (int r = 0; r < rows() && r + this.scroll < offers.size(); r++) {
            Net.OfferView o = offers.get(r + this.scroll);
            int y = py + 34 + r * ROW;
            boolean hover = mx >= px + 6 && mx < px + pw - 6 && my >= y && my < y + ROW - 2;
            UI.box(g, px + 6, y, pw - 12, ROW - 2, hover ? UI.BG3 : UI.BG2, hover ? UI.BORDER_HI : UI.BORDER);
            int x = px + 12;
            // precio
            for (ItemStack st : o.price()) {
                g.renderItem(st, x, y + 4);
                boolean ok = has(st) >= st.getCount();
                String c = String.valueOf(st.getCount());
                g.drawString(this.font, c, x + 17 - this.font.width(c), y + 13, ok ? 0xFFFFFFFF : UI.BAD, true);
                if (mx >= x && mx < x + 16 && my >= y + 4 && my < y + 20) {
                    g.setTooltipForNextFrame(this.font, st, mx, my);
                }
                x += 20;
            }
            if (!o.price().isEmpty()) {
                g.drawString(this.font, "➜", x + 2, y + 8, UI.MUTED, false);
                x += 14;
            }
            // lo que da
            if (!o.result().isEmpty()) {
                g.renderItem(o.result(), x, y + 4);
                g.renderItemDecorations(this.font, o.result(), x, y + 4);
                if (mx >= x && mx < x + 16 && my >= y + 4 && my < y + 20) {
                    g.setTooltipForNextFrame(this.font, o.result(), mx, my);
                }
                x += 20;
            }
            String label = !o.label().isEmpty() ? o.label() : o.result().isEmpty() ? "" : o.result().getHoverName().getString();
            int maxW = px + pw - 76 - x;
            g.drawString(this.font, UI.fit(this.font, label, maxW), x + 2, y + (o.left() >= 0 ? 4 : 8), UI.TEXT, false);
            if (o.left() >= 0) {
                g.drawString(this.font, o.left() == 0 ? "Ya no quedan" : "Quedan " + o.left(), x + 2, y + 14, o.left() == 0 ? UI.BAD : UI.MUTED, false);
            }
        }
        if (offers.size() > rows()) {
            String s = (this.scroll + 1) + "-" + Math.min(offers.size(), this.scroll + rows()) + " de " + offers.size() + "  (ruedita)";
            g.drawString(this.font, s, px + pw - 10 - this.font.width(s), py + ph + 3, 0xA0FFFFFF, false);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        this.scroll -= (int) Math.signum(dy);
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        if (!this.closedByServer) {
            ClientPlayNetworking.send(new Net.Buy(this.shop.id(), -1));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
