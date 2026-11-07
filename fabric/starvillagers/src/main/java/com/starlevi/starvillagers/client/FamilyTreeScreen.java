package com.starlevi.starvillagers.client;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starvillagers.family.FamilyNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** El arbol familiar: abuelos, padres, hermanos, pareja e hijos. Tocar a alguien muestra su arbol. */
public final class FamilyTreeScreen extends Screen {
    private static final int BW = 92, BH = 30;
    private final FamilyNet.Tree tree;

    private record Box(FamilyNet.Node n, int x, int y) {
    }

    private final List<Box> boxes = new ArrayList<>();
    private Box center, mate;
    private final List<Box> parents = new ArrayList<>(), kids = new ArrayList<>();

    public FamilyTreeScreen(FamilyNet.Tree tree) {
        super(Component.literal("Árbol familiar"));
        this.tree = tree;
    }

    private static String role(FamilyNet.Node n) {
        boolean f = n.female();
        return switch (n.role()) {
            case "abuelo" -> f ? "Abuela" : "Abuelo";
            case "padre" -> f ? "Mamá" : "Papá";
            case "hermano" -> f ? "Hermana" : "Hermano";
            case "esposo" -> f ? "Esposa" : "Esposo";
            case "pareja" -> f ? "Novia" : "Novio";
            case "hijo" -> f ? "Hija" : "Hijo";
            default -> n.player() ? "Jugador" : "";
        };
    }

    private void row(List<FamilyNet.Node> l, int y, List<Box> into) {
        int gap = 10, total = l.size() * BW + Math.max(0, l.size() - 1) * gap;
        int x = (this.width - total) / 2;
        for (FamilyNet.Node n : l) {
            Box b = new Box(n, x, y);
            this.boxes.add(b);
            if (into != null) {
                into.add(b);
            }
            if (n.role().equals("yo")) {
                this.center = b;
            } else if (n.role().equals("esposo") || n.role().equals("pareja")) {
                this.mate = b;
            }
            x += BW + gap;
        }
    }

    @Override
    protected void init() {
        this.boxes.clear();
        this.parents.clear();
        this.kids.clear();
        this.center = this.mate = null;
        List<FamilyNet.Node> g = new ArrayList<>(), p = new ArrayList<>(), mid = new ArrayList<>(), k = new ArrayList<>();
        FamilyNet.Node me = null, spouse = null;
        List<FamilyNet.Node> sibs = new ArrayList<>();
        for (FamilyNet.Node n : this.tree.nodes()) {
            switch (n.role()) {
                case "abuelo" -> g.add(n);
                case "padre" -> p.add(n);
                case "hijo" -> k.add(n);
                case "hermano" -> sibs.add(n);
                case "esposo", "pareja" -> spouse = n;
                case "yo" -> me = n;
                default -> {
                }
            }
        }
        // en el medio: hermanos, yo y mi pareja
        mid.addAll(sibs.subList(0, Math.min(sibs.size(), 4)));
        if (me != null) {
            mid.add(me);
        }
        if (spouse != null) {
            mid.add(spouse);
        }
        int top = 44, step = BH + 26;
        row(g.subList(0, Math.min(6, g.size())), top, null);
        row(p, top + step, this.parents);
        row(mid, top + step * 2, null);
        row(k.subList(0, Math.min(8, k.size())), top + step * 3, this.kids);
        addRenderableWidget(new StarButton(this.width / 2 - 50, this.height - 28, 100, 20, "Cerrar", StarButton.Style.GHOST, this::onClose));
    }

    private void line(GuiGraphics gr, int x1, int y1, int x2, int y2, int c) {
        if (y1 == y2) {
            gr.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, c);
        } else {
            int my = (y1 + y2) / 2;
            gr.fill(x1, Math.min(y1, my), x1 + 1, Math.max(y1, my) + 1, c);
            gr.fill(Math.min(x1, x2), my, Math.max(x1, x2) + 1, my + 1, c);
            gr.fill(x2, Math.min(my, y2), x2 + 1, Math.max(my, y2) + 1, c);
        }
    }

    @Override
    public void render(GuiGraphics gr, int mx, int my, float pt) {
        super.render(gr, mx, my, pt);
        String who = this.center != null ? this.center.n().name() : "?";
        UI.title(gr, this.font, "Familia de " + who, this.width / 2, 16);
        int lc = UI.BORDER_HI;
        // lineas: padres -> yo, yo + pareja -> hijos
        if (this.center != null) {
            int cx = this.center.x() + BW / 2, cy = this.center.y();
            for (Box b : this.parents) {
                line(gr, b.x() + BW / 2, b.y() + BH, cx, cy, lc);
            }
            int kx = cx, ky = this.center.y() + BH;
            if (this.mate != null) {
                int y = this.center.y() + BH / 2;
                line(gr, this.center.x() + BW, y, this.mate.x(), y, UI.ACCENT);
                kx = (this.center.x() + BW + this.mate.x()) / 2;
                ky = y;
            }
            for (Box b : this.kids) {
                line(gr, kx, ky, b.x() + BW / 2, b.y(), lc);
            }
        }
        for (Box b : this.boxes) {
            FamilyNet.Node n = b.n();
            boolean hover = mx >= b.x() && mx < b.x() + BW && my >= b.y() && my < b.y() + BH;
            int fill = n.dead() ? 0xEE2A2A2E : n.female() ? 0xEE3A2036 : 0xEE1F2A3E;
            int border = n.role().equals("yo") ? UI.GOLD : hover ? UI.ACCENT : n.player() ? UI.GOLD : UI.BORDER;
            UI.box(gr, b.x(), b.y(), BW, BH, fill, border);
            String name = (n.dead() ? "† " : "") + n.name();
            gr.drawString(this.font, UI.fit(this.font, name, BW - 8), b.x() + (BW - this.font.width(UI.fit(this.font, name, BW - 8))) / 2, b.y() + 5,
                    n.dead() ? UI.MUTED : UI.TEXT, false);
            String r = role(n);
            gr.drawString(this.font, r, b.x() + (BW - this.font.width(r)) / 2, b.y() + 17, n.female() ? 0xFFF7A8D8 : 0xFF9CC8FF, false);
        }
        if (this.tree.nodes().size() <= 1) {
            String t = "Todavía no tiene familia anotada.";
            gr.drawString(this.font, t, (this.width - this.font.width(t)) / 2, this.height / 2 + 30, UI.MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (Box b : this.boxes) {
            if (e.x() >= b.x() && e.x() < b.x() + BW && e.y() >= b.y() && e.y() < b.y() + BH && !b.n().role().equals("yo")) {
                ClientPlayNetworking.send(new FamilyNet.Ask(b.n().id()));
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
