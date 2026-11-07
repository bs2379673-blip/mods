package com.starlevi.starvillagers.client.library;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starvillagers.look.Wardrobe;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Mirar la biblioteca de skins de MCA (ropa y pelo de la comunidad) y ponerle algo a un aldeano. */
public final class LibraryScreen extends Screen {
    private static final int COLS = 6, ROWS = 2, PER = COLS * ROWS, CW = 66, CH = 96;
    private final int entity;
    private final String who;
    private String kind = "clothing", order = "likes", search = "";
    private int page;
    private List<Library.Entry> entries = new ArrayList<>();
    private Library.Entry selected;
    private String status = "Cargando...";
    private int request;
    private EditBox box;

    public LibraryScreen(int entity, String who) {
        super(Component.literal("Biblioteca de skins"));
        this.entity = entity;
        this.who = who;
    }

    private int gx() {
        return (this.width - COLS * CW) / 2;
    }

    private int gy() {
        return 74;
    }

    private void load() {
        int r = ++this.request;
        this.status = "Cargando...";
        this.entries = new ArrayList<>();
        this.selected = null;
        Library.list(this.kind, this.search, this.order, this.page, PER).thenAccept(l -> Minecraft.getInstance().execute(() -> {
            if (r != this.request) {
                return;
            }
            if (l == null) {
                this.status = "No me pude conectar con la biblioteca (mca.conczin.net).";
            } else {
                this.entries = l;
                this.status = l.isEmpty() ? "No hay nada con eso." : "";
            }
        }));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(new StarButton(cx - 150, 30, 70, 18, "Ropa", this.kind.equals("clothing") ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
            this.kind = "clothing";
            this.page = 0;
            rebuildWidgets();
            load();
        }));
        addRenderableWidget(new StarButton(cx - 78, 30, 70, 18, "Pelo", this.kind.equals("hair") ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
            this.kind = "hair";
            this.page = 0;
            rebuildWidgets();
            load();
        }));
        addRenderableWidget(new StarButton(cx - 4, 30, 74, 18, this.order.equals("likes") ? "♥ Más gustadas" : "✦ Nuevas", StarButton.Style.NORMAL, () -> {
            this.order = this.order.equals("likes") ? "date" : "likes";
            this.page = 0;
            rebuildWidgets();
            load();
        }));
        this.box = new EditBox(this.font, cx + 74, 30, 52, 18, Component.literal("Buscar"));
        this.box.setValue(this.search);
        this.box.setHint(Component.literal("buscar"));
        addRenderableWidget(this.box);
        addRenderableWidget(new StarButton(cx + 128, 30, 22, 18, "Ir", StarButton.Style.NORMAL, () -> {
            this.search = this.box.getValue();
            this.page = 0;
            load();
        }));
        int fy = this.height - 28;
        addRenderableWidget(new StarButton(cx - 200, fy, 30, 20, "◀", StarButton.Style.NORMAL, () -> {
            if (this.page > 0) {
                this.page--;
                load();
            }
        }));
        addRenderableWidget(new StarButton(cx - 166, fy, 30, 20, "▶", StarButton.Style.NORMAL, () -> {
            if (this.entries.size() >= PER) {
                this.page++;
                load();
            }
        }));
        addRenderableWidget(new StarButton(cx - 126, fy, 120, 20, "✔ Ponerle esto", StarButton.Style.ACCENT, () -> {
            if (this.selected != null) {
                ClientPlayNetworking.send(new Wardrobe.Set(this.entity, this.kind.equals("hair") ? "pelo" : "ropa", this.selected.id(), this.selected.version()));
                this.status = "¡Listo! Se lo puse.";
            }
        }));
        addRenderableWidget(new StarButton(cx, fy, 120, 20, "↺ Lo de siempre", StarButton.Style.NORMAL, () -> {
            ClientPlayNetworking.send(new Wardrobe.Set(this.entity, this.kind.equals("hair") ? "pelo" : "ropa", -1, 0));
            this.status = "Volvió a lo de siempre.";
        }));
        addRenderableWidget(new StarButton(cx + 126, fy, 74, 20, "Cerrar", StarButton.Style.GHOST, this::onClose));
        if (this.entries.isEmpty() && this.request == 0) {
            load();
        }
    }

    /** La skin de frente, como un muñequito (capa de abajo y de arriba). */
    private static void doll(GuiGraphics g, Identifier t, int x, int y, int s) {
        int[][] parts = {
                // u, v, w, h, x, y (en pixeles de la skin, dentro del muñeco de 16x32)
                {8, 8, 8, 8, 4, 0}, {40, 8, 8, 8, 4, 0},
                {20, 20, 8, 12, 4, 8}, {20, 36, 8, 12, 4, 8},
                {44, 20, 4, 12, 0, 8}, {44, 36, 4, 12, 0, 8},
                {36, 52, 4, 12, 12, 8}, {52, 52, 4, 12, 12, 8},
                {4, 20, 4, 12, 4, 20}, {4, 36, 4, 12, 4, 20},
                {20, 52, 4, 12, 8, 20}, {4, 52, 4, 12, 8, 20}};
        for (int[] p : parts) {
            g.blit(RenderPipelines.GUI_TEXTURED, t, x + p[4] * s, y + p[5] * s, p[0], p[1], p[2] * s, p[3] * s, p[2], p[3], 64, 64);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UI.title(g, this.font, "Biblioteca de skins de MCA · para " + this.who, this.width / 2, 12);
        int x0 = gx(), y0 = gy();
        for (int i = 0; i < this.entries.size() && i < PER; i++) {
            Library.Entry e = this.entries.get(i);
            int x = x0 + (i % COLS) * CW, y = y0 + (i / COLS) * (CH + 6);
            boolean hover = mx >= x && mx < x + CW - 4 && my >= y && my < y + CH;
            UI.box(g, x, y, CW - 4, CH, UI.BG2, e == this.selected ? UI.GOLD : hover ? UI.ACCENT : UI.BORDER);
            Identifier t = Library.texture(e.id(), e.version());
            if (t != null) {
                doll(g, t, x + (CW - 4 - 32) / 2, y + 4, 2);
            } else {
                g.drawString(this.font, "...", x + (CW - 4 - this.font.width("...")) / 2, y + 30, UI.MUTED, false);
            }
            String title = UI.fit(this.font, e.title(), CW - 8);
            g.drawString(this.font, title, x + (CW - 4 - this.font.width(title)) / 2, y + 70, UI.TEXT, false);
            String l = "♥ " + e.likes();
            g.drawString(this.font, l, x + (CW - 4 - this.font.width(l)) / 2, y + 82, 0xFFFF8FB0, false);
        }
        if (!this.status.isEmpty()) {
            g.drawString(this.font, this.status, (this.width - this.font.width(this.status)) / 2, y0 + 2 * (CH + 6) + 4, UI.MUTED, false);
        }
        String pg = "Página " + (this.page + 1);
        g.drawString(this.font, pg, this.width / 2 - 200, this.height - 40, UI.MUTED, false);
        if (this.selected != null) {
            String sel = "Elegida: " + this.selected.title() + " (de " + this.selected.author() + ")";
            g.drawString(this.font, UI.fit(this.font, sel, 300), this.width / 2 - 126, this.height - 40, UI.GOLD, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        int x0 = gx(), y0 = gy();
        for (int i = 0; i < this.entries.size() && i < PER; i++) {
            int x = x0 + (i % COLS) * CW, y = y0 + (i / COLS) * (CH + 6);
            if (ev.x() >= x && ev.x() < x + CW - 4 && ev.y() >= y && ev.y() < y + CH) {
                this.selected = this.entries.get(i);
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
