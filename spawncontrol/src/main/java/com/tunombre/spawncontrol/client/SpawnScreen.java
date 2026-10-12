package com.tunombre.spawncontrol.client;

import com.tunombre.spawncontrol.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pantalla de administracion de spawns (/spawns gui). Cada boton manda el comando /spawns equivalente. */
public class SpawnScreen extends Screen {
    private static final int ROW_H = 22;
    private List<Net.Row> rows;
    private final List<GuiEventListener> rowWidgets = new ArrayList<>();
    private EditBox filterBox, radiusBox;
    private String filter = "";
    private String radius = "64";
    private int page;
    private int perPage = 8;
    private List<Net.Row> shown = new ArrayList<>();

    public SpawnScreen(List<Net.Row> rows) {
        super(Component.literal("Control de spawns"));
        this.rows = rows;
    }

    public static void update(Net.Snapshot s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SpawnScreen sc) {
            sc.rows = s.rows();
            sc.layoutRows();
        } else if (s.open()) {
            mc.setScreen(new SpawnScreen(s.rows()));
        }
    }

    private static void send(String cmd) { Net.CH.sendToServer(new Net.Cmd(cmd)); }

    @Override
    protected void init() {
        rowWidgets.clear();
        int cx = width / 2;
        filterBox = new EditBox(font, cx - 165, 24, 150, 18, Component.literal("Buscar"));
        filterBox.setHint(Component.literal("Buscar mob o mod..."));
        filterBox.setValue(filter);
        filterBox.setResponder(v -> { filter = v; page = 0; layoutRows(); });
        addRenderableWidget(filterBox);
        radiusBox = new EditBox(font, cx + 40, 24, 40, 18, Component.literal("Radio"));
        radiusBox.setValue(radius);
        radiusBox.setFilter(v -> v.matches("\\d{0,4}"));
        radiusBox.setResponder(v -> radius = v);
        addRenderableWidget(radiusBox);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; layoutRows(); } }).bounds(cx - 165, height - 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * perPage < shown.size()) { page++; layoutRows(); } }).bounds(cx - 141, height - 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose()).bounds(cx + 105, height - 28, 60, 20).build());
        perPage = Math.max(3, (height - 100) / ROW_H);
        layoutRows();
    }

    private void layoutRows() {
        for (GuiEventListener w : rowWidgets) removeWidget(w);
        rowWidgets.clear();
        String f = filter.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (Net.Row r : rows) if (f.isEmpty() || r.id().toLowerCase(Locale.ROOT).contains(f)) shown.add(r);
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        int cx = width / 2, y = 52;
        for (int i = page * perPage; i < Math.min(shown.size(), (page + 1) * perPage); i++) {
            Net.Row r = shown.get(i);
            String id = r.id().equals("*") ? "todos:todos" : r.id().endsWith(":*") ? r.id().substring(0, r.id().length() - 1) + "todos" : r.id();
            int x = cx - 165 + 150 + 4;
            add(Button.builder(Component.literal(r.state() == 2 ? "Activar" : "Desactivar"), b -> send(r.state() == 2 ? "spawns activar " + id + " silencio" : "spawns desactivar " + id))
                    .bounds(x, y, 52, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Enciende o apaga el spawn natural de este mob (sin aviso)"))).build());
            add(Button.builder(Component.literal("Liberar"), b -> send("spawns activar " + id))
                    .bounds(x + 54, y, 46, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Activa el mob y lanza su aviso (imagen de tacui) a todos"))).build());
            add(Button.builder(Component.literal("Zona aqui"), b -> {
                String name = "z" + (System.currentTimeMillis() % 100000);
                int rad = 64;
                try { rad = Math.max(1, Integer.parseInt(radius)); } catch (Exception ignored) { }
                send("spawns zona aqui " + id + " " + name + " " + rad + " permitir");
                send("spawns zona solo " + id + " true");
            }).bounds(x + 102, y, 52, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Crea una zona permitida en tu posicion (radio de arriba) y el mob solo spawneara en zonas"))).build());
            add(Button.builder(Component.literal("X"), b -> send("spawns reset " + id)).bounds(x + 156, y, 20, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Borra la regla: vuelve a spawnear como siempre"))).build());
            y += ROW_H;
        }
    }

    private void add(Button b) {
        addRenderableWidget(b);
        rowWidgets.add(b);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Control de spawns", width / 2, 8, 0xFFFFFF);
        g.drawString(font, "Radio:", width / 2 - 2, 29, 0xAAAAAA, false);
        int cx = width / 2, y = 52;
        for (int i = page * perPage; i < Math.min(shown.size(), (page + 1) * perPage); i++) {
            Net.Row r = shown.get(i);
            String name = r.id();
            if (font.width(name) > 148) name = font.plainSubstrByWidth(name, 144) + "..";
            g.drawString(font, name, cx - 165, y + 1, 0xFFFFFF, false);
            String st;
            int col;
            if (r.state() == 0) { st = "normal"; col = 0x888888; }
            else if (r.state() == 2) { st = r.day() > 0 && !r.released() ? "dia " + r.day() : "OFF"; col = r.day() > 0 && !r.released() ? 0xFFCC33 : 0xFF5555; }
            else { st = "ON"; col = 0x55FF55; }
            StringBuilder ex = new StringBuilder(st);
            if (r.only()) ex.append(" solo-zonas");
            if (r.zones() > 0) ex.append(" z").append(r.zones());
            if (r.max() > 0) ex.append(" max").append(r.max());
            if (r.prob() < 100) ex.append(" ").append(r.prob()).append("%");
            if (r.ann()) ex.append(" aviso");
            g.drawString(font, ex.toString(), cx - 165, y + 11, col, false);
            y += ROW_H;
        }
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        g.drawCenteredString(font, (page + 1) + " / " + pages + "  (" + shown.size() + " mobs)", cx - 40, height - 22, 0xAAAAAA);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
