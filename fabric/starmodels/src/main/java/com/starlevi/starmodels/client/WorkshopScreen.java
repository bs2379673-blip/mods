package com.starlevi.starmodels.client;

import com.starlevi.starmodels.client.ui.StarButton;
import com.starlevi.starmodels.client.ui.UI;
import com.starlevi.starmodels.model.AvatarBuilder;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * El workshop: elegir personaje, sacarle accesorios, ponerle accesorios de otros personajes y
 * acomodarlos (posicion y tamano). La ropa 3D va pegada al cuerpo de cada personaje y no se cambia.
 * Lo que se prueba se ve en uno mismo (en la vista) y nadie mas lo ve hasta guardar.
 */
public class WorkshopScreen extends Screen {
    private enum Tab {
        PROPIOS, AGREGAR, PUESTOS
    }

    private static final int ROWS = 9;
    private String base;
    private final HashSet<String> without = new HashSet<>();
    private final List<ClientAvatars.Added> with = new ArrayList<>();
    private Tab tab = Tab.PROPIOS;
    private int pageLeft, pageRight;
    /** En la pestaña agregar: de que personaje se estan viendo los accesorios. */
    private String source;
    private int selected = -1;
    private boolean waiting;
    private String status = "";

    public WorkshopScreen() {
        super(Component.literal("StarModels"));
        Minecraft mc = Minecraft.getInstance();
        ClientAvatars.Look l = mc.player == null ? null : ClientAvatars.Look.parse(ClientAvatars.rawOutfit(mc.player.getUUID()));
        if (l != null) {
            this.base = l.base();
            this.without.addAll(l.without());
            this.with.addAll(l.with());
        }
        apply();
    }

    private ClientAvatars.Look current() {
        return this.base == null ? null : new ClientAvatars.Look(this.base, new HashSet<>(this.without), new ArrayList<>(this.with));
    }

    private void apply() {
        ClientAvatars.preview(current());
    }

    private void rebuild() {
        apply();
        clearWidgets();
        init();
    }

    private int px() {
        return Math.max(4, this.width / 2 - 230);
    }

    private int py() {
        return Math.max(4, this.height / 2 - 120);
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        this.waiting = false;
        // --- izquierda: personajes
        List<Net.Avatar> all = ClientAvatars.index();
        int pages = Math.max(1, (all.size() + ROWS - 1) / ROWS);
        this.pageLeft = Math.min(this.pageLeft, pages - 1);
        addRenderableWidget(new StarButton(x + 8, y + 26, 134, 16, "Mi skin de Minecraft", this.base == null ? StarButton.Style.TAB_ON : StarButton.Style.TAB,
                () -> {
                    this.base = null;
                    this.without.clear();
                    this.with.clear();
                    rebuild();
                }).left());
        for (int i = 0; i < ROWS; i++) {
            int idx = this.pageLeft * ROWS + i;
            if (idx >= all.size()) {
                break;
            }
            Net.Avatar a = all.get(idx);
            boolean on = a.id().equals(this.base);
            addRenderableWidget(new StarButton(x + 8, y + 44 + i * 18, 134, 16, UI.fit(this.font, a.name(), 120), on ? StarButton.Style.TAB_ON : StarButton.Style.TAB,
                    () -> {
                        if (!a.id().equals(this.base)) {
                            this.base = a.id();
                            this.without.clear();
                            this.with.clear();
                            this.selected = -1;
                        }
                        rebuild();
                    }).left());
        }
        if (pages > 1) {
            addRenderableWidget(new StarButton(x + 8, y + 210, 30, 16, "<", StarButton.Style.GHOST, () -> {
                this.pageLeft = (this.pageLeft + pages - 1) % pages;
                rebuild();
            }));
            addRenderableWidget(new StarButton(x + 112, y + 210, 30, 16, ">", StarButton.Style.GHOST, () -> {
                this.pageLeft = (this.pageLeft + 1) % pages;
                rebuild();
            }));
        }
        // --- derecha: pestañas
        int rx = x + 300;
        String[] names = {"Suyos", "Agregar", "Puestos"};
        for (int t = 0; t < 3; t++) {
            Tab tt = Tab.values()[t];
            addRenderableWidget(new StarButton(rx + t * 52, y + 26, 50, 16, names[t], this.tab == tt ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
                this.tab = tt;
                this.pageRight = 0;
                rebuild();
            }));
        }
        if (this.base != null) {
            switch (this.tab) {
                case PROPIOS -> own(rx, y + 46);
                case AGREGAR -> add(rx, y + 46);
                case PUESTOS -> worn(rx, y + 46);
            }
        }
        // --- abajo
        addRenderableWidget(new StarButton(x + 300, y + 230, 74, 18, "Guardar", StarButton.Style.ACCENT, () -> {
            ClientAvatars.Look l = current();
            ClientPlayNetworking.send(new Net.Choose(l == null ? "" : l.toJson()));
            onClose();
        }));
        addRenderableWidget(new StarButton(x + 378, y + 230, 74, 18, "Cancelar", StarButton.Style.NORMAL, this::onClose));
    }

    private void pager(int x, int y, int total) {
        int pages = Math.max(1, (total + ROWS - 1) / ROWS);
        this.pageRight = Math.min(this.pageRight, pages - 1);
        if (pages > 1) {
            addRenderableWidget(new StarButton(x, y, 30, 16, "<", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + pages - 1) % pages;
                rebuild();
            }));
            addRenderableWidget(new StarButton(x + 122, y, 30, 16, ">", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + 1) % pages;
                rebuild();
            }));
        }
    }

    private static String label(AvatarBuilder.Accessory a) {
        return a.name + " · " + AvatarBuilder.PART_NAMES[a.part];
    }

    /** Los accesorios del personaje: prender o apagar. */
    private void own(int x, int y) {
        ClientAvatars.Ready r = ClientAvatars.get(this.base);
        if (r == null) {
            this.waiting = true;
            this.status = "Cargando personaje...";
            return;
        }
        List<AvatarBuilder.Accessory> acc = r.model.accessories;
        this.status = acc.isEmpty() ? "Este personaje no tiene accesorios sueltos" : "";
        for (int i = 0; i < ROWS; i++) {
            int idx = this.pageRight * ROWS + i;
            if (idx >= acc.size()) {
                break;
            }
            AvatarBuilder.Accessory a = acc.get(idx);
            boolean on = !this.without.contains(a.id);
            addRenderableWidget(new StarButton(x, y + i * 18, 152, 16, (on ? "✔ " : "✘ ") + UI.fit(this.font, label(a), 128),
                    on ? StarButton.Style.CHOICE : StarButton.Style.GHOST, () -> {
                if (!this.without.remove(a.id)) {
                    this.without.add(a.id);
                }
                rebuild();
            }).left());
        }
        pager(x, y + 164, acc.size());
    }

    /** Accesorios de otros personajes: primero el personaje, despues el accesorio. */
    private void add(int x, int y) {
        if (this.source == null) {
            List<Net.Avatar> all = ClientAvatars.index();
            all.removeIf(a -> a.id().equals(this.base));
            this.status = all.isEmpty() ? "No hay otros personajes" : "¿De que personaje?";
            for (int i = 0; i < ROWS; i++) {
                int idx = this.pageRight * ROWS + i;
                if (idx >= all.size()) {
                    break;
                }
                Net.Avatar a = all.get(idx);
                addRenderableWidget(new StarButton(x, y + i * 18, 152, 16, UI.fit(this.font, a.name(), 140), StarButton.Style.TAB, () -> {
                    this.source = a.id();
                    this.pageRight = 0;
                    rebuild();
                }).left());
            }
            pager(x, y + 164, all.size());
            return;
        }
        addRenderableWidget(new StarButton(x, y, 152, 16, "← " + UI.fit(this.font, nameOf(this.source), 130), StarButton.Style.GHOST, () -> {
            this.source = null;
            this.pageRight = 0;
            rebuild();
        }).left());
        ClientAvatars.Ready r = ClientAvatars.get(this.source);
        if (r == null) {
            this.waiting = true;
            this.status = "Cargando personaje...";
            return;
        }
        List<AvatarBuilder.Accessory> acc = r.model.accessories;
        this.status = acc.isEmpty() ? "Ese personaje no tiene accesorios sueltos" : "Tocá para ponertelo";
        for (int i = 0; i < ROWS - 1; i++) {
            int idx = this.pageRight * (ROWS - 1) + i;
            if (idx >= acc.size()) {
                break;
            }
            AvatarBuilder.Accessory a = acc.get(idx);
            String src = this.source;
            addRenderableWidget(new StarButton(x, y + 18 + i * 18, 152, 16, "+ " + UI.fit(this.font, label(a), 136), StarButton.Style.TAB, () -> {
                this.with.add(new ClientAvatars.Added(src, a.id, 0, 0, 0, 1));
                this.selected = this.with.size() - 1;
                this.tab = Tab.PUESTOS;
                this.pageRight = 0;
                rebuild();
            }).left());
        }
        int pages = Math.max(1, (acc.size() + ROWS - 2) / (ROWS - 1));
        if (pages > 1) {
            addRenderableWidget(new StarButton(x, y + 164, 30, 16, "<", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + pages - 1) % pages;
                rebuild();
            }));
            addRenderableWidget(new StarButton(x + 122, y + 164, 30, 16, ">", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + 1) % pages;
                rebuild();
            }));
        }
    }

    private static String nameOf(String id) {
        Net.Avatar a = ClientAvatars.info(id);
        return a == null ? id : a.name();
    }

    private String accName(ClientAvatars.Added ad) {
        ClientAvatars.Ready r = ClientAvatars.get(ad.from());
        if (r != null) {
            for (AvatarBuilder.Accessory a : r.model.accessories) {
                if (a.id.equals(ad.acc())) {
                    return a.name;
                }
            }
        }
        return ad.acc();
    }

    /** Los agregados: elegir uno y acomodarlo. */
    private void worn(int x, int y) {
        this.status = this.with.isEmpty() ? "Todavia no agregaste nada" : "";
        int shown = Math.min(this.with.size(), 4);
        for (int i = 0; i < shown; i++) {
            int idx = this.pageRight * 4 + i;
            if (idx >= this.with.size()) {
                break;
            }
            int k = idx;
            addRenderableWidget(new StarButton(x, y + i * 18, 152, 16, UI.fit(this.font, accName(this.with.get(k)), 140),
                    k == this.selected ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
                this.selected = k;
                rebuild();
            }).left());
        }
        int pages = Math.max(1, (this.with.size() + 3) / 4);
        if (pages > 1) {
            addRenderableWidget(new StarButton(x + 154, y, 20, 16, "▲", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + pages - 1) % pages;
                rebuild();
            }));
            addRenderableWidget(new StarButton(x + 154, y + 54, 20, 16, "▼", StarButton.Style.GHOST, () -> {
                this.pageRight = (this.pageRight + 1) % pages;
                rebuild();
            }));
        }
        if (this.selected < 0 || this.selected >= this.with.size()) {
            return;
        }
        int ay = y + 80;
        String[] axis = {"X", "Y", "Z", "Tamaño"};
        for (int a = 0; a < 4; a++) {
            int ax = a;
            int row = ay + a * 18;
            addRenderableWidget(new StarButton(x + 60, row, 20, 16, "−", StarButton.Style.NORMAL, () -> nudge(ax, -1)));
            addRenderableWidget(new StarButton(x + 132, row, 20, 16, "+", StarButton.Style.NORMAL, () -> nudge(ax, 1)));
            String lbl = axis[a];
            addRenderableWidget(new StarButton(x, row, 56, 16, lbl, StarButton.Style.GHOST, () -> {
            }).left());
        }
        addRenderableWidget(new StarButton(x, ay + 74, 74, 16, "Centrar", StarButton.Style.NORMAL, () -> {
            ClientAvatars.Added o = this.with.get(this.selected);
            this.with.set(this.selected, new ClientAvatars.Added(o.from(), o.acc(), 0, 0, 0, 1));
            rebuild();
        }));
        addRenderableWidget(new StarButton(x + 78, ay + 74, 74, 16, "Quitar", StarButton.Style.DANGER, () -> {
            this.with.remove(this.selected);
            this.selected = -1;
            rebuild();
        }));
    }

    /** Medio pixel por toque (con Shift, 2 pixeles); el tamaño de a 5 %. */
    private void nudge(int axis, int dir) {
        ClientAvatars.Added o = this.with.get(this.selected);
        float step = Minecraft.getInstance().hasShiftDown() ? 2f : 0.5f;
        float x = o.x(), y = o.y(), z = o.z(), s = o.s();
        switch (axis) {
            case 0 -> x += dir * step;
            case 1 -> y -= dir * step; // en el modelo y crece hacia abajo
            case 2 -> z += dir * step;
            default -> s = Math.max(0.2f, Math.min(4f, s + dir * (Minecraft.getInstance().hasShiftDown() ? 0.2f : 0.05f)));
        }
        this.with.set(this.selected, new ClientAvatars.Added(o.from(), o.acc(), x, y, z, s));
        rebuild();
    }

    @Override
    public void tick() {
        if (this.waiting) {
            String id = this.tab == Tab.AGREGAR && this.source != null ? this.source : this.base;
            if (id != null && ClientAvatars.get(id) != null) {
                rebuild();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = px(), y = py();
        UI.panel(g, x, y, 460, 256);
        g.drawString(this.font, "✦ StarModels · Workshop", x + 10, y + 9, UI.ACCENT, false);
        g.drawString(this.font, "Personajes", x + 10, y + 16 + 2, UI.MUTED, false);
        UI.box(g, x + 148, y + 26, 146, 222, 0xFF221E2A, UI.BORDER);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 149, y + 27, x + 293, y + 247, 70, 0.0625f, mx, my, mc.player);
        }
        if (this.tab == Tab.PUESTOS && this.selected >= 0 && this.selected < this.with.size()) {
            ClientAvatars.Added o = this.with.get(this.selected);
            float[] v = {o.x(), -o.y(), o.z()};
            int ay = y + 46 + 80;
            for (int a = 0; a < 3; a++) {
                String t = String.format("%.1f", v[a]);
                g.drawString(this.font, t, x + 300 + 106 - this.font.width(t) / 2, ay + a * 18 + 4, UI.TEXT, false);
            }
            String t = Math.round(o.s() * 100) + "%";
            g.drawString(this.font, t, x + 300 + 106 - this.font.width(t) / 2, ay + 3 * 18 + 4, UI.TEXT, false);
            g.drawString(this.font, "Shift: pasos grandes", x + 300, ay + 94, UI.MUTED, false);
        }
        if (!this.status.isEmpty()) {
            g.drawString(this.font, UI.fit(this.font, this.status, 156), x + 300, y + 214, UI.MUTED, false);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public void onClose() {
        ClientAvatars.endPreview();
        super.onClose();
    }

    @Override
    public void removed() {
        ClientAvatars.endPreview();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
